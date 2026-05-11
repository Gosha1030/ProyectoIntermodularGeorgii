package georgii.sytnik.thothtasks.net;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public final class AccessClient {

    public static class Result {
        public final boolean granted;
        public final String reason;
        public Result(boolean granted, String reason) {
            this.granted = granted;
            this.reason = reason;
        }
    }

    private AccessClient() {}

    public static Result requestAccess(
            String ip,
            int port,
            String resourceIdHex,
            String externalName,
            int timeoutMs
    ) throws Exception {

        JSONObject body = new JSONObject();
        body.put("resourceId", resourceIdHex);
        body.put("name", externalName); // helps owner display name / group requests

        JSONObject req = MessageCodec.envelope(
                Protocol.ACCESS_REQUEST,
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
            if (!Protocol.ACCESS_RESULT.equals(env.optString("type"))) {
                return new Result(false, "UNEXPECTED_" + env.optString("type"));
            }

            JSONObject b = env.optJSONObject("body");
            if (b == null) return new Result(false, "NO_BODY");

            boolean granted = b.optBoolean("granted", false);
            String reason = b.optString("reason", "UNKNOWN");
            return new Result(granted, reason);
        }
    }
}