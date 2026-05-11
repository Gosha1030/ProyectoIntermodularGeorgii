package georgii.sytnik.thothtasks.net;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public final class UdpClient {

    private UdpClient() {}

    public static JSONObject requestScheduleSummary(
            String ip,
            int port,
            String resourceIdHex,
            String externalName,
            long startDayUtcMs,
            int days,
            int timeoutMs
    ) throws Exception {

        JSONObject body = new JSONObject();
        body.put("resourceId", resourceIdHex);
        body.put("name", externalName);
        body.put("startDayUtcMs", startDayUtcMs);
        body.put("days", days);

        JSONObject req = MessageCodec.envelope(
                Protocol.SCHEDULE_SUMMARY_REQUEST,
                "consumer",
                System.currentTimeMillis(),
                null,
                body
        );

        byte[] reqBytes = MessageCodec.encode(req);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            DatagramPacket p = new DatagramPacket(reqBytes, reqBytes.length, InetAddress.getByName(ip), port);
            socket.send(p);

            byte[] buf = new byte[64 * 1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            JSONObject env = MessageCodec.decode(resp.getData(), resp.getLength());
            if (!Protocol.SCHEDULE_SUMMARY_RESPONSE.equals(env.optString("type"))) {
                throw new IllegalStateException("Unexpected response: " + env.optString("type"));
            }
            return env.optJSONObject("body");
        }
    }
}
