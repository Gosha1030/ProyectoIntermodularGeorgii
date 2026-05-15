package georgii.sytnik.thothtasks.net;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public final class VersionClient {

    private VersionClient() {
    }

    public static long requestRemoteVersion(String ip, int port, String resourceIdHex, String externalName, int timeoutMs) throws Exception {

        JSONObject body = new JSONObject();
        body.put("resourceId", resourceIdHex);
        body.put("name", externalName);

        JSONObject req = MessageCodec.envelope(Protocol.VERSION_REQUEST, "consumer", System.currentTimeMillis(), null, body);

        byte[] reqBytes = MessageCodec.encode(req);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);

            DatagramPacket p = new DatagramPacket(reqBytes, reqBytes.length, InetAddress.getByName(ip), port);
            socket.send(p);

            byte[] buf = new byte[64 * 1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            JSONObject env = MessageCodec.decode(resp.getData(), resp.getLength());
            if (!Protocol.VERSION_RESPONSE.equals(env.optString("type"))) {
                throw new IllegalStateException("Unexpected response: " + env.optString("type"));
            }

            JSONObject b = env.optJSONObject("body");
            if (b == null) return 0;
            return b.optLong("remoteVersion", 0);
        }
    }
}