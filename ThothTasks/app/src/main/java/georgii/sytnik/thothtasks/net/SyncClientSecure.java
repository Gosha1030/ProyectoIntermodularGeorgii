package georgii.sytnik.thothtasks.net;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SyncClientSecure {

    public interface ProgressListener {
        void onProgress(int receivedChunks, int totalChunks);
    }

    public static class SyncResult {
        public final long remoteVersion;
        public final JSONArray tasks;
        public final JSONArray taskChanges;
        public SyncResult(long remoteVersion, JSONArray tasks, JSONArray taskChanges) {
            this.remoteVersion = remoteVersion;
            this.tasks = tasks;
            this.taskChanges = taskChanges;
        }
    }

    private SyncClientSecure() {}

    public static SyncResult syncResourceSecure(
            Context ctx,
            georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity src,
            String externalName,
            long sinceVersion,
            int socketTimeoutMs,
            ProgressListener progress
    ) throws Exception {

        String ridHex = MessageCodec.hex(src.resourceId);

        NetSession s = HandshakeClient.ensureSession(ctx, src.ip, src.port, ridHex, externalName);

        JSONObject body = new JSONObject();
        body.put("resourceId", ridHex);
        body.put("name", externalName);
        body.put("sinceVersion", sinceVersion);

        JSONObject req = MessageCodec.envelope(Protocol.SYNC_REQUEST, "consumer", System.currentTimeMillis(), null, body);
        req.put("rid", ridHex);
        req.put("seq", s.nextSeqOut++);
        req = SecureCodec.wrapAuth(s, req);

        byte[] reqBytes = MessageCodec.encode(req);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(socketTimeoutMs);

            socket.send(new DatagramPacket(reqBytes, reqBytes.length, InetAddress.getByName(src.ip), src.port));

            Map<Integer, JSONObject> chunks = new HashMap<>();
            Set<String> seenMsgIds = new HashSet<>();

            int totalChunks = -1;
            long remoteVersion = 0;
            JSONArray tasksAll = new JSONArray();
            JSONArray changesAll = new JSONArray();

            long startWait = System.currentTimeMillis();
            long overallTimeout = 60_000;

            while (System.currentTimeMillis() - startWait < overallTimeout) {
                byte[] buf = new byte[64 * 1024];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);

                try {
                    socket.receive(resp);
                } catch (Exception timeout) {
                    continue;
                }

                JSONObject env = MessageCodec.decode(resp.getData(), resp.getLength());
                env.put("rid", ridHex);

                String type = env.optString("type", "");
                String msgId = env.optString("msgId", "");
                boolean firstTime = seenMsgIds.add(msgId);

                if (Protocol.SYNC_CHUNK.equals(type) || Protocol.SYNC_DONE.equals(type)) {
                    sendAckAuth(socket, resp.getAddress(), resp.getPort(), ridHex, s, msgId);
                }

                if (Protocol.SYNC_CHUNK.equals(type)) {
                    JSONObject b = SecureCodec.unwrapAead(s, env);

                    int idx = b.optInt("chunkIndex", -1);
                    int tot = b.optInt("totalChunks", -1);
                    if (idx < 0 || tot <= 0) continue;

                    totalChunks = tot;
                    remoteVersion = Math.max(remoteVersion, b.optLong("remoteVersion", 0));

                    if (firstTime && !chunks.containsKey(idx)) {
                        chunks.put(idx, b);
                        if (progress != null) progress.onProgress(chunks.size(), totalChunks);
                    }
                }

                if (Protocol.SYNC_DONE.equals(type)) {
                    JSONObject b = SecureCodec.unwrapAead(s, env);
                    remoteVersion = Math.max(remoteVersion, b.optLong("remoteVersion", 0));

                    if (totalChunks > 0 && chunks.size() == totalChunks) {
                        for (int i = 0; i < totalChunks; i++) {
                            JSONObject ch = chunks.get(i);
                            if (ch == null) continue;

                            JSONArray tPart = ch.optJSONArray("tasks");
                            if (tPart != null) for (int k = 0; k < tPart.length(); k++) tasksAll.put(tPart.get(k));

                            JSONArray cPart = ch.optJSONArray("taskChanges");
                            if (cPart != null) for (int k = 0; k < cPart.length(); k++) changesAll.put(cPart.get(k));
                        }
                        return new SyncResult(remoteVersion, tasksAll, changesAll);
                    }
                }
            }

            throw new IllegalStateException("SYNC timeout: received " + chunks.size() + "/" + totalChunks);
        }
    }

    private static void sendAckAuth(DatagramSocket socket, InetAddress addr, int port, String ridHex, NetSession s, String ackOfMsgId) {
        try {
            JSONObject body = new JSONObject();
            body.put("ok", true);

            JSONObject ack = MessageCodec.envelope(Protocol.ACK, "consumer", System.currentTimeMillis(), ackOfMsgId, body);
            ack.put("rid", ridHex);
            ack.put("seq", s.nextSeqOut++);
            ack = SecureCodec.wrapAuth(s, ack);

            byte[] bytes = MessageCodec.encode(ack);
            socket.send(new DatagramPacket(bytes, bytes.length, addr, port));
        } catch (Exception ignored) {}
    }
}