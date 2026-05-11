package georgii.sytnik.thothtasks.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SyncClient {

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

    private SyncClient() {}

    public static SyncResult syncResource(
            String ip,
            int port,
            String resourceIdHex,
            String externalName,
            long sinceVersion,
            int timeoutMs,
            ProgressListener progress
    ) throws Exception {

        JSONObject body = new JSONObject();
        body.put("resourceId", resourceIdHex);
        body.put("name", externalName);
        body.put("sinceVersion", sinceVersion);

        JSONObject req = MessageCodec.envelope(
                Protocol.SYNC_REQUEST,
                "consumer",
                System.currentTimeMillis(),
                null,
                body
        );

        byte[] reqBytes = MessageCodec.encode(req);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);

            // send SYNC_REQUEST (no need reliable on consumer side; owner will respond reliably)
            DatagramPacket p = new DatagramPacket(reqBytes, reqBytes.length, InetAddress.getByName(ip), port);
            socket.send(p);

            Map<Integer, JSONObject> chunks = new HashMap<>();
            Set<String> seenMsgIds = new HashSet<>();

            int totalChunks = -1;
            long remoteVersion = 0;

            JSONArray tasksAll = new JSONArray();
            JSONArray changesAll = new JSONArray();

            long startWait = System.currentTimeMillis();
            long overallTimeout = 60_000; // 60s overall for v1

            while (System.currentTimeMillis() - startWait < overallTimeout) {
                byte[] buf = new byte[64 * 1024];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);

                try {
                    socket.receive(resp);
                } catch (Exception timeout) {
                    // keep waiting; owner retries missing chunks
                    continue;
                }

                JSONObject env = MessageCodec.decode(resp.getData(), resp.getLength());
                String type = env.optString("type", "");
                String msgId = env.optString("msgId", "");

                // Dedup (still ACK again below)
                boolean firstTime = seenMsgIds.add(msgId);

                // Always ACK any SYNC_CHUNK / SYNC_DONE to stop retries
                if (Protocol.SYNC_CHUNK.equals(type) || Protocol.SYNC_DONE.equals(type)) {
                    sendAck(socket, resp.getAddress(), resp.getPort(), msgId);
                }

                if (Protocol.SYNC_CHUNK.equals(type)) {
                    JSONObject b = env.optJSONObject("body");
                    if (b == null) continue;

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
                    JSONObject b = env.optJSONObject("body");
                    if (b != null) remoteVersion = Math.max(remoteVersion, b.optLong("remoteVersion", 0));

                    // if complete, assemble and return
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
                    // else keep waiting; owner should resend missing chunks (because no ACK)
                }
            }

            throw new IllegalStateException("SYNC timeout: received " + chunks.size() + "/" + totalChunks);
        }
    }

    private static void sendAck(DatagramSocket socket, InetAddress addr, int port, String ackOfMsgId) {
        try {
            JSONObject ack = MessageCodec.envelope(
                    Protocol.ACK,
                    "consumer",
                    System.currentTimeMillis(),
                    ackOfMsgId,
                    null
            );
            byte[] bytes = MessageCodec.encode(ack);
            DatagramPacket p = new DatagramPacket(bytes, bytes.length, addr, port);
            socket.send(p);
        } catch (Exception ignored) {}
    }
}