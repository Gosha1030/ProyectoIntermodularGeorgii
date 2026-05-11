package georgii.sytnik.thothtasks.net;

import android.content.Context;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.security.B64;
import georgii.sytnik.thothtasks.security.Ecdh;
import georgii.sytnik.thothtasks.security.Hkdf;
import georgii.sytnik.thothtasks.security.HmacAuth;
import georgii.sytnik.thothtasks.security.Kdf;
import georgii.sytnik.thothtasks.security.SessionSecrets;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.security.UserSettingsCrypto;

public final class HandshakeClient {

    private HandshakeClient() {}

    public static String sessionKey(String ip, int port, String ridHex) {
        return ip + ":" + port + "|" + ridHex;
    }

    public static NetSession ensureSession(Context ctx, String ip, int port, String ridHex, String externalName) throws Exception {
        String key = sessionKey(ip, port, ridHex);
        NetSession existing = NetSessionStore.get(key);
        if (existing != null) return existing;

        AppDatabase db = AppDatabase.get(ctx);

        byte[] userId = SessionStore.loadLastUserId(ctx);
        if (userId == null) throw new IllegalStateException("No logged user");
        UserEntity u = db.userDao().findById(userId);
        if (u == null) throw new IllegalStateException("User not found");

        UserSettingsCrypto.Params params = UserSettingsCrypto.ensureParams(u);
        if (params.changed) db.userDao().update(u);

        char[] pwd = SessionSecrets.getPassword();
        if (pwd == null || pwd.length == 0) throw new IllegalStateException("Password required");

        // NOTE: Server sends same salt/iters in challenge; we use server values to ensure match.
        byte[] nonceA = new byte[16];
        new SecureRandom().nextBytes(nonceA);

        KeyPair ephA = Ecdh.generateEphemeral();

        JSONObject helloBody = new JSONObject();
        helloBody.put("rid", ridHex);
        helloBody.put("name", externalName);
        helloBody.put("nonceA", B64.enc(nonceA));
        helloBody.put("ephPubA", Ecdh.pubToB64(ephA.getPublic()));

        JSONObject hello = MessageCodec.envelope(Protocol.SESSION_HELLO, "consumer", System.currentTimeMillis(), null, helloBody);
        hello.put("rid", ridHex);

        JSONObject challengeEnv;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2500);
            byte[] out = MessageCodec.encode(hello);
            socket.send(new DatagramPacket(out, out.length, InetAddress.getByName(ip), port));

            byte[] buf = new byte[64 * 1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            challengeEnv = MessageCodec.decode(resp.getData(), resp.getLength());
        }

        if (!Protocol.SESSION_CHALLENGE.equals(challengeEnv.optString("type"))) {
            throw new IllegalStateException("Expected SESSION_CHALLENGE got " + challengeEnv.optString("type"));
        }

        JSONObject chBody = challengeEnv.getJSONObject("body");
        byte[] nonceB = B64.dec(chBody.getString("nonceB"));
        String ephPubB64 = chBody.getString("ephPubB");
        byte[] saltServer = B64.dec(chBody.getString("saltB64"));
        int itersServer = chBody.optInt("iters", 200_000);

        byte[] kMaster = Kdf.pbkdf2(pwd, saltServer, itersServer, 32);

        PublicKey pubB = Ecdh.pubFromB64(ephPubB64);
        byte[] shared = Ecdh.sharedSecret(ephA, pubB);

        byte[] proof = HmacAuth.mac(kMaster, concat(nonceA, nonceB, ridHex.getBytes("UTF-8")));
        byte[] prk = Hkdf.extract(proof, shared);

        NetSession s = new NetSession();
        s.sessionId = MessageCodec.hex(Hkdf.expand(prk, "sid".getBytes("UTF-8"), 16));
        s.kAuth = Hkdf.expand(prk, "auth".getBytes("UTF-8"), 32);
        s.kAead = Hkdf.expand(prk, "aead".getBytes("UTF-8"), 32);
        s.noncePrefix4 = Hkdf.expand(prk, "nonce".getBytes("UTF-8"), 4);
        s.lastSeqIn = 0;
        s.nextSeqOut = 1;

        JSONObject resBody = new JSONObject();
        resBody.put("rid", ridHex);
        resBody.put("name", externalName);
        resBody.put("sessionId", s.sessionId);
        resBody.put("proof", B64.enc(proof));

        JSONObject result = MessageCodec.envelope(Protocol.SESSION_RESULT, "consumer", System.currentTimeMillis(), null, resBody);
        result.put("rid", ridHex);

        JSONObject okEnv;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2500);
            byte[] out = MessageCodec.encode(result);
            socket.send(new DatagramPacket(out, out.length, InetAddress.getByName(ip), port));

            byte[] buf = new byte[64 * 1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            okEnv = MessageCodec.decode(resp.getData(), resp.getLength());
        }

        if (!Protocol.SESSION_RESULT.equals(okEnv.optString("type"))) {
            throw new IllegalStateException("Expected SESSION_RESULT(ok) got " + okEnv.optString("type"));
        }

        JSONObject okBody = okEnv.getJSONObject("body");
        if (!okBody.optBoolean("ok", false)) {
            throw new IllegalStateException("Handshake rejected: " + okBody.optString("reason", "UNKNOWN"));
        }

        NetSessionStore.put(key, s);
        return s;
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] r = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        System.arraycopy(c, 0, r, a.length + b.length, c.length);
        return r;
    }
}