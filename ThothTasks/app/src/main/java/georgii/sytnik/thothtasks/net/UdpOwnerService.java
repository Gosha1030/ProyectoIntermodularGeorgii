package georgii.sytnik.thothtasks.net;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.AccessGrantEntity;
import georgii.sytnik.thothtasks.db.entities.AccessRequestEntity;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;
import georgii.sytnik.thothtasks.db.entities.PendingOutboxEntity;
import georgii.sytnik.thothtasks.db.entities.ReceivedInboxEntity;
import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.security.B64;
import georgii.sytnik.thothtasks.security.Ecdh;
import georgii.sytnik.thothtasks.security.Hkdf;
import georgii.sytnik.thothtasks.security.HmacAuth;
import georgii.sytnik.thothtasks.security.Kdf;
import georgii.sytnik.thothtasks.security.SessionSecrets;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.security.UserSettingsCrypto;
import georgii.sytnik.thothtasks.time.UuidV7;

public class UdpOwnerService extends Service {

    private AppDatabase db;
    private Thread retryThread;
    private RetryScheduler retryScheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        db = AppDatabase.get(this);

        retryScheduler = new RetryScheduler(this);
        retryThread = new Thread(retryScheduler, "udp-retry");
        retryThread.start();

        new Thread(this::startListeners, "udp-owner-listeners").start();
    }

    private void startListeners() {
        try {
            byte[] ownerId = SessionStore.loadLastUserId(this);
            if (ownerId == null) return;

            List<ShareResourceEntity> resources = db.shareResourceDao().listForOwner(ownerId);
            for (ShareResourceEntity r : resources) {
                if (!r.active || r.port == null) continue;
                int port = r.port;
                new Thread(() -> listenLoop(port), "udp-listen-" + port).start();
            }
        } catch (Exception ignored) {}
    }

    private void listenLoop(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buf = new byte[64 * 1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String peerIp = packet.getAddress().getHostAddress();
                int peerPort = packet.getPort();

                JSONObject env;
                try {
                    env = MessageCodec.decode(packet.getData(), packet.getLength());
                } catch (Exception e) {
                    continue;
                }

                String msgIdHex = env.optString("msgId", null);
                if (msgIdHex == null) continue;

                // dedup
                byte[] msgId = MessageCodec.hexToBytes(msgIdHex);
                if (db.inboxDao().exists(msgId) > 0) {
                    // already processed => ACK again (signed if possible)
                    sendAckSmart(peerIp, peerPort, env, msgIdHex);
                    continue;
                }

                ReceivedInboxEntity inbox = new ReceivedInboxEntity();
                inbox.msgId = msgId;
                inbox.receivedAtUtcMs = System.currentTimeMillis();
                db.inboxDao().insert(inbox);

                // ACK received message (smart)
                sendAckSmart(peerIp, peerPort, env, msgIdHex);

                // handle message
                try {
                    handle(peerIp, peerPort, env);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Smart ACK:
     * - if we have a session for (peerIp:peerPort|rid) -> send AUTH ACK
     * - else -> send plain ACK (handshake messages)
     */
    private void sendAckSmart(String peerIp, int peerPort, JSONObject receivedEnv, String ackOfHex) {
        try {
            String ridHex = receivedEnv.optString("rid", "");
            String peerKey = peerIp + ":" + peerPort + "|" + ridHex;

            NetSession s = NetSessionStore.get(peerKey);

            JSONObject body = new JSONObject();
            body.put("ok", true);

            JSONObject ack = MessageCodec.envelope(
                    Protocol.ACK,
                    "owner",
                    System.currentTimeMillis(),
                    ackOfHex,
                    body
            );
            if (!ridHex.isEmpty()) ack.put("rid", ridHex);

            if (s != null && !ridHex.isEmpty()) {
                ack.put("seq", s.nextSeqOut++);
                ack = SecureCodec.wrapAuth(s, ack);
            }

            sendRaw(this, peerIp, peerPort, ack);
        } catch (Exception ignored) {}
    }

    private void handle(String peerIp, int peerPort, JSONObject env) throws Exception {
        String type = env.optString("type", "");
        String ridHex = env.optString("rid", "");
        String peerKey = peerIp + ":" + peerPort + "|" + ridHex;

        // --- unwrap secure envelopes ---
        JSONObject body;
        JSONObject sec = env.optJSONObject("sec");
        if (sec != null) {
            NetSession s = NetSessionStore.get(peerKey);
            if (s == null) {
                // no session -> ignore; client will auto-handshake
                return;
            }

            // anti-replay minimal (monotonic seq)
            long seq = env.optLong("seq", 0);
            if (seq <= s.lastSeqIn) return;
            s.lastSeqIn = seq;

            String mode = sec.optString("mode", "");
            if (SecureCodec.MODE_AUTH.equals(mode)) {
                body = SecureCodec.unwrapAuth(s, env);
            } else if (SecureCodec.MODE_AEAD.equals(mode)) {
                body = SecureCodec.unwrapAead(s, env);
            } else {
                return;
            }
        } else {
            body = env.optJSONObject("body");
            if (body == null) body = new JSONObject();
        }

        switch (type) {

            // ---------------- Handshake (PLAIN) ----------------

            case Protocol.SESSION_HELLO: {
                HandshakeCache.cleanupOld(System.currentTimeMillis());

                String rid = body.optString("rid", "");
                if (rid.isEmpty()) return;

                byte[] ownerId = SessionStore.loadLastUserId(this);
                if (ownerId == null) return;

                UserEntity owner = db.userDao().findById(ownerId);
                if (owner == null) return;

                // Ensure salt/iters exist in Ajustes
                UserSettingsCrypto.Params params = UserSettingsCrypto.ensureParams(owner);
                if (params.changed) db.userDao().update(owner);

                String nonceA_b64 = body.optString("nonceA", "");
                String ephPubA_b64 = body.optString("ephPubA", "");
                if (nonceA_b64.isEmpty() || ephPubA_b64.isEmpty()) return;

                byte[] nonceA = B64.dec(nonceA_b64);

                KeyPair ephB = Ecdh.generateEphemeral();
                byte[] nonceB = new byte[16];
                new SecureRandom().nextBytes(nonceB);

                HandshakeCache.Pending p = new HandshakeCache.Pending();
                p.createdAtUtcMs = System.currentTimeMillis();
                p.ridHex = rid;
                p.peerKey = peerKey;
                p.nonceA = nonceA;
                p.nonceB = nonceB;
                p.ephPubA_B64 = ephPubA_b64;
                p.ephB = ephB;
                p.salt = params.salt;
                p.iters = params.iters;

                HandshakeCache.put(peerKey, p);

                JSONObject chBody = new JSONObject();
                chBody.put("rid", rid);
                chBody.put("nonceB", B64.enc(nonceB));
                chBody.put("ephPubB", Ecdh.pubToB64(ephB.getPublic()));
                chBody.put("saltB64", B64.enc(params.salt));
                chBody.put("iters", params.iters);

                JSONObject chEnv = MessageCodec.envelope(
                        Protocol.SESSION_CHALLENGE,
                        "owner",
                        System.currentTimeMillis(),
                        env.optString("msgId"),
                        chBody
                );
                chEnv.put("rid", rid);

                sendRaw(this, peerIp, peerPort, chEnv);
                break;
            }

            case Protocol.SESSION_RESULT: {
                String rid = body.optString("rid", "");
                if (rid.isEmpty()) return;

                HandshakeCache.Pending p = HandshakeCache.get(peerKey);
                if (p == null) {
                    sendHandshakeResult(peerIp, peerPort, rid, env.optString("msgId"), false, "NO_PENDING");
                    return;
                }
                // PasswordRequired: solo exigimos contraseña si el owner la requiere
                byte[] ownerId = SessionStore.loadLastUserId(this);
                UserEntity owner = (ownerId != null) ? db.userDao().findById(ownerId) : null;

                // default seguro: si no sabemos, exigimos password
                boolean requirePwd = (owner == null) || owner.passwordRequired;

                char[] pwd = SessionSecrets.getPassword();
                if (requirePwd) {
                    if (pwd == null || pwd.length == 0) {
                        HandshakeCache.remove(peerKey);
                        sendHandshakeResult(peerIp, peerPort, rid, env.optString("msgId"), false, "OWNER_NO_PASSWORD");
                        return;
                    }
                } else {
                    // Password no requerido => tratamos como password vacío
                    if (pwd == null) pwd = new char[0];
                }
                String sessionId = body.optString("sessionId", "");
                String proofB64 = body.optString("proof", "");
                if (sessionId.isEmpty() || proofB64.isEmpty()) {
                    sendHandshakeResult(peerIp, peerPort, rid, env.optString("msgId"), false, "BAD_RESULT");
                    return;
                }

                byte[] proof = B64.dec(proofB64);

                // derive K_master and verify proof
                byte[] kMaster = Kdf.pbkdf2(pwd, p.salt, p.iters, 32);

                byte[] expectedProof = HmacAuth.mac(
                        kMaster,
                        concat(p.nonceA, p.nonceB, rid.getBytes(StandardCharsets.UTF_8))
                );

                if (!java.security.MessageDigest.isEqual(expectedProof, proof)) {
                    HandshakeCache.remove(peerKey);
                    sendHandshakeResult(peerIp, peerPort, rid, env.optString("msgId"), false, "BAD_PROOF");
                    return;
                }

                // compute shared and derive session keys
                PublicKey pubA = Ecdh.pubFromB64(p.ephPubA_B64);
                byte[] shared = Ecdh.sharedSecret(p.ephB, pubA);

                byte[] prk = Hkdf.extract(proof, shared);

                String expectedSid = MessageCodec.hex(Hkdf.expand(prk, "sid".getBytes(StandardCharsets.UTF_8), 16));
                if (!expectedSid.equals(sessionId)) {
                    HandshakeCache.remove(peerKey);
                    sendHandshakeResult(peerIp, peerPort, rid, env.optString("msgId"), false, "SID_MISMATCH");
                    return;
                }

                NetSession s = new NetSession();
                s.sessionId = expectedSid;
                s.kAuth = Hkdf.expand(prk, "auth".getBytes(StandardCharsets.UTF_8), 32);
                s.kAead = Hkdf.expand(prk, "aead".getBytes(StandardCharsets.UTF_8), 32);
                s.noncePrefix4 = Hkdf.expand(prk, "nonce".getBytes(StandardCharsets.UTF_8), 4);
                s.lastSeqIn = 0;
                s.nextSeqOut = 1;

                NetSessionStore.put(peerKey, s);
                HandshakeCache.remove(peerKey);

                sendHandshakeResult(peerIp, peerPort, rid, env.optString("msgId"), true, "OK");
                break;
            }

            // ---------------- Secure system messages (AUTH) ----------------

            case Protocol.ACK: {
                // If ACK came AUTH, it was already verified in unwrapAuth.
                // Remove from outbox by ackOf
                String ackOf = env.optString("ackOf", null);
                if (ackOf != null) {
                    db.outboxDao().delete(MessageCodec.hexToBytes(ackOf));
                }
                break;
            }

            case Protocol.VERSION_REQUEST: {
                // Must be AUTH (body already verified)
                String resourceIdHex = body.optString("resourceId", "");
                byte[] resourceId = MessageCodec.hexToBytes(resourceIdHex);
                ShareResourceEntity res = db.shareResourceDao().findById(resourceId);
                if (res == null || !res.active) return;

                if (!checkGranted(peerIp, peerPort, ridHex, resourceIdHex, env.optString("msgId"), body.optString("name", peerIp + ":" + peerPort))) return;

                long remoteVersion = db.taskChangeDao().maxCreateAtForTaskTree(res.rootTaskId);

                NetSession s = NetSessionStore.get(peerKey);
                if (s == null) return;

                JSONObject respBody = new JSONObject();
                respBody.put("resourceId", resourceIdHex);
                respBody.put("remoteVersion", remoteVersion);

                JSONObject respEnv = MessageCodec.envelope(
                        Protocol.VERSION_RESPONSE,
                        "owner",
                        System.currentTimeMillis(),
                        env.optString("msgId"),
                        respBody
                );
                respEnv.put("rid", ridHex);
                respEnv.put("seq", s.nextSeqOut++);

                respEnv = SecureCodec.wrapAuth(s, respEnv);
                sendRaw(this, peerIp, peerPort, respEnv);
                break;
            }

            case Protocol.SCHEDULE_SUMMARY_REQUEST: {
                // Must be AUTH, response AEAD
                String resourceIdHex = body.optString("resourceId", "");
                byte[] resourceId = MessageCodec.hexToBytes(resourceIdHex);

                ShareResourceEntity res = db.shareResourceDao().findById(resourceId);
                if (res == null || !res.active) return;

                if (!checkGranted(peerIp, peerPort, ridHex, resourceIdHex, env.optString("msgId"), body.optString("name", peerIp + ":" + peerPort))) return;

                long startDayUtcMs = body.optLong("startDayUtcMs", System.currentTimeMillis());
                int days = body.optInt("days", 30);

                JSONObject summary = georgii.sytnik.thothtasks.domain.schedule.ScheduleSummaryBuilder
                        .buildFixedSummary(db, resourceId, startDayUtcMs, days);

                NetSession s = NetSessionStore.get(peerKey);
                if (s == null) return;

                JSONObject respBody = new JSONObject();
                respBody.put("resourceId", resourceIdHex);
                respBody.put("startDayUtcMs", summary.getLong("startDayUtcMs"));
                respBody.put("daysCount", summary.getInt("daysCount"));
                respBody.put("days", summary.getJSONArray("days"));

                JSONObject respEnv = MessageCodec.envelope(
                        Protocol.SCHEDULE_SUMMARY_RESPONSE,
                        "owner",
                        System.currentTimeMillis(),
                        env.optString("msgId"),
                        respBody
                );
                respEnv.put("rid", ridHex);
                respEnv.put("seq", s.nextSeqOut++);

                respEnv = SecureCodec.wrapAead(s, respEnv);
                sendRaw(this, peerIp, peerPort, respEnv);
                break;
            }

            case Protocol.SYNC_REQUEST: {
                // Must be AUTH, chunks AEAD + reliable
                String resourceIdHex = body.optString("resourceId", "");
                long sinceVersion = body.optLong("sinceVersion", 0);
                byte[] resourceId = MessageCodec.hexToBytes(resourceIdHex);

                ShareResourceEntity res = db.shareResourceDao().findById(resourceId);
                if (res == null || !res.active) return;

                if (!checkGranted(peerIp, peerPort, ridHex, resourceIdHex, env.optString("msgId"), body.optString("name", peerIp + ":" + peerPort))) return;

                JSONObject payload = georgii.sytnik.thothtasks.domain.sync.SyncPayloadBuilder
                        .build(db, resourceId, sinceVersion);

                if (payload == null) return;

                JSONArray tasks = payload.getJSONArray("tasks");
                JSONArray changes = payload.getJSONArray("taskChanges");
                long remoteVersion = payload.getLong("remoteVersion");

                int maxPerChunk = 10; // avoid fragmentation
                int total = Math.max(tasks.length(), changes.length());
                int totalChunks = Math.max(1, (int) Math.ceil(total / (double) maxPerChunk));

                NetSession s = NetSessionStore.get(peerKey);
                if (s == null) return;

                for (int i = 0; i < totalChunks; i++) {
                    JSONObject chunkBody = new JSONObject();
                    chunkBody.put("resourceId", resourceIdHex);
                    chunkBody.put("chunkIndex", i);
                    chunkBody.put("totalChunks", totalChunks);
                    chunkBody.put("remoteVersion", remoteVersion);

                    JSONArray tPart = new JSONArray();
                    JSONArray cPart = new JSONArray();

                    for (int j = i * maxPerChunk; j < Math.min(tasks.length(), (i + 1) * maxPerChunk); j++) {
                        tPart.put(tasks.get(j));
                    }
                    for (int j = i * maxPerChunk; j < Math.min(changes.length(), (i + 1) * maxPerChunk); j++) {
                        cPart.put(changes.get(j));
                    }

                    chunkBody.put("tasks", tPart);
                    chunkBody.put("taskChanges", cPart);

                    JSONObject chunkEnv = MessageCodec.envelope(
                            Protocol.SYNC_CHUNK,
                            "owner",
                            System.currentTimeMillis(),
                            null,
                            chunkBody
                    );
                    chunkEnv.put("rid", ridHex);
                    chunkEnv.put("seq", s.nextSeqOut++);

                    chunkEnv = SecureCodec.wrapAead(s, chunkEnv);

                    sendReliable(peerIp, peerPort, peerKey, chunkEnv);
                }

                JSONObject doneBody = new JSONObject();
                doneBody.put("resourceId", resourceIdHex);
                doneBody.put("remoteVersion", remoteVersion);

                JSONObject doneEnv = MessageCodec.envelope(
                        Protocol.SYNC_DONE,
                        "owner",
                        System.currentTimeMillis(),
                        null,
                        doneBody
                );
                doneEnv.put("rid", ridHex);
                doneEnv.put("seq", s.nextSeqOut++);

                doneEnv = SecureCodec.wrapAead(s, doneEnv);

                sendReliable(peerIp, peerPort, peerKey, doneEnv);
                break;
            }

            default:
                // ignore
                break;
        }
    }

    /** Returns true if peer has grant for resourceId. */
    private boolean checkGranted(String peerIp, int peerPort, String ridHex, String resourceIdHex, String reqMsgIdHex, String externalName) {
        try {
            byte[] ownerId = SessionStore.loadLastUserId(this);
            if (ownerId == null) return false;

            UserEntity owner = db.userDao().findById(ownerId);
            if (owner == null) return false;

            ExternalUserEntity eu = findOrCreateExternalUser(db, ownerId, peerIp, peerPort, externalName);
            if (eu.blocked) return false;

            byte[] resourceId = MessageCodec.hexToBytes(resourceIdHex);
            ShareResourceEntity res = db.shareResourceDao().findById(resourceId);
            if (res == null) return false;

            // ✅ Si el owner NO requiere confirmación: permitir siempre
            if (!owner.confirmRequired) return true;

            // ✅ ConfirmRequired: debe existir grant activo para este recurso
            AccessGrantEntity g = db.accessGrantDao().find(eu.externalId, res.resourceId);
            boolean ok = (g != null && g.granted && g.revokedAtUtcMs == null);
            if (ok) return true;

            // Persistente: crear/actualizar solicitud PENDING una sola vez hasta decisión
            long now = System.currentTimeMillis();
            boolean notify = false;

            AccessRequestEntity ar = db.accessRequestDao().find(eu.externalId, res.resourceId);
            if (ar == null) {
                ar = new AccessRequestEntity();
                ar.requestId = MessageCodec.uuidToBytes(UuidV7.newUuid());
                ar.externalUserId = eu.externalId;
                ar.resourceId = res.resourceId;
                ar.state = AccessRequestEntity.STATE_PENDING;
                ar.createdAtUtcMs = now;
                ar.lastNotifiedAtUtcMs = now;
                ar.peerIp = peerIp;
                ar.peerPort = peerPort;
                ar.requestMsgIdHex = reqMsgIdHex;
                ar.externalName = externalName;
                db.accessRequestDao().upsert(ar);
                notify = true;
            } else {
                // si sigue pendiente, actualizamos datos (msgId/ip/port/nombre) sin volver a notificar
                if (AccessRequestEntity.STATE_PENDING.equals(ar.state)) {
                    ar.peerIp = peerIp;
                    ar.peerPort = peerPort;
                    ar.requestMsgIdHex = reqMsgIdHex;
                    ar.externalName = externalName;
                    db.accessRequestDao().upsert(ar);
                }
            }

            if (notify) {
                maybeNotifyAccessOnce(eu, res, peerIp, peerPort, resourceIdHex, reqMsgIdHex, externalName);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendHandshakeResult(String ip, int port, String rid, String ackOf, boolean ok, String reason) {
        try {
            JSONObject out = new JSONObject();
            out.put("ok", ok);
            out.put("reason", reason);

            JSONObject resp = MessageCodec.envelope(
                    Protocol.SESSION_RESULT,
                    "owner",
                    System.currentTimeMillis(),
                    ackOf,
                    out
            );
            resp.put("rid", rid);
            sendRaw(this, ip, port, resp);
        } catch (Exception ignored) {}
    }

    private void sendReliable(String peerIp, int peerPort, String peerKey, JSONObject env) throws Exception {
        String payloadJson = env.toString();
        String msgIdHex = env.getString("msgId");
        byte[] msgIdBytes = MessageCodec.hexToBytes(msgIdHex);

        PendingOutboxEntity e = new PendingOutboxEntity();
        e.msgId = msgIdBytes;
        e.peerKey = peerKey; // ip:port|rid
        e.payloadJson = payloadJson;
        e.attempts = 0;
        e.createdUtcMs = System.currentTimeMillis();
        e.nextRetryUtcMs = e.createdUtcMs + 1000;
        db.outboxDao().upsert(e);

        sendRawString(peerIp, peerPort, payloadJson);
    }

    public static ExternalUserEntity findOrCreateExternalUser(AppDatabase db, byte[] ownerUserId, String ip, int port, String name) {
        ExternalUserEntity existing = db.externalUserDao().findByIpPort(ownerUserId, ip, port);
        if (existing != null) return existing;

        ExternalUserEntity e = new ExternalUserEntity();
        e.externalId = MessageCodec.uuidToBytes(UuidV7.newUuid());
        e.ownerUserId = ownerUserId;
        e.externalUserName = name;
        e.externalUserNickname = null;
        e.ip = ip;
        e.port = port;
        e.blocked = false;

        db.externalUserDao().insert(e);
        return e;
    }

    public static void sendRaw(Context ctx, String ip, int port, JSONObject env) throws Exception {
        byte[] data = MessageCodec.encode(env);
        DatagramSocket s = new DatagramSocket();
        DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
        s.send(p);
        s.close();
    }

    private static void sendRawString(String ip, int port, String payloadJson) throws Exception {
        byte[] data = payloadJson.getBytes(StandardCharsets.UTF_8);
        DatagramSocket s = new DatagramSocket();
        DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
        s.send(p);
        s.close();
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] r = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        System.arraycopy(c, 0, r, a.length + b.length, c.length);
        return r;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (retryScheduler != null) retryScheduler.stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    // --- Access request notification (ConfirmRequired) ---
    private static final String ACCESS_CHANNEL_ID = "thoth_access_requests";

    private void ensureAccessChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel existing = nm.getNotificationChannel(ACCESS_CHANNEL_ID);
            if (existing != null) return;
            NotificationChannel ch = new NotificationChannel(
                    ACCESS_CHANNEL_ID,
                    "Access requests",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Requests to access shared resources");
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) {}
    }

    private void maybeNotifyAccessOnce(ExternalUserEntity eu,
                                       ShareResourceEntity res,
                                       String peerIp,
                                       int peerPort,
                                       String resourceIdHex,
                                       String reqMsgIdHex,
                                       String externalName) {
        try {
            if (reqMsgIdHex == null || reqMsgIdHex.isEmpty()) return;

            ensureAccessChannel();

            Intent accept = new Intent(this, AccessDecisionReceiver.class);
            accept.setAction(Protocol.ACTION_ACCEPT);
            accept.putExtra(Protocol.EXTRA_PEER_IP, peerIp);
            accept.putExtra(Protocol.EXTRA_PEER_PORT, peerPort);
            accept.putExtra(Protocol.EXTRA_RESOURCE_ID_HEX, resourceIdHex);
            accept.putExtra(Protocol.EXTRA_REQUEST_MSGID_HEX, reqMsgIdHex);
            accept.putExtra(Protocol.EXTRA_EXTERNAL_NAME, externalName);

            Intent reject = new Intent(this, AccessDecisionReceiver.class);
            reject.setAction(Protocol.ACTION_REJECT);
            reject.putExtra(Protocol.EXTRA_PEER_IP, peerIp);
            reject.putExtra(Protocol.EXTRA_PEER_PORT, peerPort);
            reject.putExtra(Protocol.EXTRA_RESOURCE_ID_HEX, resourceIdHex);
            reject.putExtra(Protocol.EXTRA_REQUEST_MSGID_HEX, reqMsgIdHex);
            reject.putExtra(Protocol.EXTRA_EXTERNAL_NAME, externalName);

            Intent block = new Intent(this, AccessDecisionReceiver.class);
            block.setAction(Protocol.ACTION_BLOCK);
            block.putExtra(Protocol.EXTRA_PEER_IP, peerIp);
            block.putExtra(Protocol.EXTRA_PEER_PORT, peerPort);
            block.putExtra(Protocol.EXTRA_RESOURCE_ID_HEX, resourceIdHex);
            block.putExtra(Protocol.EXTRA_REQUEST_MSGID_HEX, reqMsgIdHex);
            block.putExtra(Protocol.EXTRA_EXTERNAL_NAME, externalName);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

            String key = MessageCodec.hex(eu.externalId) + "|" + resourceIdHex;
            int baseId = key.hashCode();

            PendingIntent piAccept = PendingIntent.getBroadcast(this, baseId + 1, accept, flags);
            PendingIntent piReject = PendingIntent.getBroadcast(this, baseId + 2, reject, flags);
            PendingIntent piBlock  = PendingIntent.getBroadcast(this, baseId + 3, block, flags);

            String title = "Access request";
            String resName = (res != null && res.name != null) ? res.name : resourceIdHex;
            String who = (externalName != null && !externalName.isEmpty()) ? externalName : (peerIp + ":" + peerPort);
            String text = who + " wants access to " + resName;

            NotificationCompat.Builder nb = new NotificationCompat.Builder(this, ACCESS_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(android.R.drawable.checkbox_on_background, "Allow", piAccept)
                    .addAction(android.R.drawable.ic_delete, "Reject", piReject)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Block", piBlock);

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(baseId, nb.build());
        } catch (Exception ignored) {}
    }
}