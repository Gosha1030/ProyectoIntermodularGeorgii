package georgii.sytnik.thothtasks.net;

import android.content.Context;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;

public final class ScheduleSummaryClientSecure {

    private ScheduleSummaryClientSecure() {}

    /**
     * Sends SCHEDULE_SUMMARY_REQUEST as AUTH and expects SCHEDULE_SUMMARY_RESPONSE as AEAD.
     * Returns decrypted body JSON:
     * { resourceId, startDayUtcMs, daysCount, days:[{offset, blocks:[{s,e}]}...] }
     */
    public static JSONObject requestSummarySecure(
            Context ctx,
            ExternalSourceEntity src,
            String externalName,
            long startDayUtcMs,
            int days,
            int timeoutMs
    ) throws Exception {

        String ridHex = MessageCodec.hex(src.resourceId);

        // Auto-handshake if needed
        NetSession s = HandshakeClient.ensureSession(ctx, src.ip, src.port, ridHex, externalName);

        // Build request body
        JSONObject body = new JSONObject();
        body.put("resourceId", ridHex);
        body.put("name", externalName);
        body.put("startDayUtcMs", startDayUtcMs);
        body.put("days", days);

        // Envelope (AUTH)
        JSONObject env = MessageCodec.envelope(
                Protocol.SCHEDULE_SUMMARY_REQUEST,
                "consumer",
                System.currentTimeMillis(),
                null,
                body
        );
        env.put("rid", ridHex);
        env.put("seq", s.nextSeqOut++);
        env = SecureCodec.wrapAuth(s, env);

        byte[] reqBytes = MessageCodec.encode(env);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);

            DatagramPacket p = new DatagramPacket(reqBytes, reqBytes.length, InetAddress.getByName(src.ip), src.port);
            socket.send(p);

            byte[] buf = new byte[64 * 1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            JSONObject respEnv = MessageCodec.decode(resp.getData(), resp.getLength());

            // Ensure rid exists for AAD binding (your SecureCodec includes rid in AAD)
            if (!respEnv.has("rid")) respEnv.put("rid", ridHex);

            String type = respEnv.optString("type", "");
            if (!Protocol.SCHEDULE_SUMMARY_RESPONSE.equals(type)) {
                throw new IllegalStateException("Unexpected response: " + type);
            }

            JSONObject sec = respEnv.optJSONObject("sec");
            if (sec == null || !SecureCodec.MODE_AEAD.equals(sec.optString("mode", ""))) {
                throw new IllegalStateException("SUMMARY_RESPONSE is not AEAD");
            }

            // Decrypt body
            return SecureCodec.unwrapAead(s, respEnv);
        }
    }
}