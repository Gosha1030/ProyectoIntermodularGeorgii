package georgii.sytnik.thothtasks.net;

import android.content.Context;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public final class VersionClientSecure {

    private VersionClientSecure() {}

    public static long requestRemoteVersionSecure(Context ctx, georgii.sytnik.thothtasks.db.AppDatabase db,
                                                  georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity src,
                                                  String externalName) throws Exception {

        String ridHex = MessageCodec.hex(src.resourceId);

        NetSession s = HandshakeClient.ensureSession(ctx, src.ip, src.port, ridHex, externalName);

        JSONObject body = new JSONObject();
        body.put("resourceId", ridHex);
        body.put("name", externalName);

        JSONObject env = MessageCodec.envelope(Protocol.VERSION_REQUEST, "consumer", System.currentTimeMillis(), null, body);
        env.put("rid", ridHex);
        env.put("seq", s.nextSeqOut++);

        env = SecureCodec.wrapAuth(s, env);

        byte[] reqBytes = MessageCodec.encode(env);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2500);
            socket.send(new DatagramPacket(reqBytes, reqBytes.length, InetAddress.getByName(src.ip), src.port));

            byte[] buf = new byte[64 * 1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            JSONObject respEnv = MessageCodec.decode(resp.getData(), resp.getLength());
            respEnv.put("rid", ridHex); // ensure aad matches (rid must exist)

            JSONObject sec = respEnv.optJSONObject("sec");
            if (sec == null || !"AUTH".equals(sec.optString("mode"))) {
                throw new IllegalStateException("VERSION_RESPONSE not AUTH");
            }

            JSONObject b = SecureCodec.unwrapAuth(s, respEnv);
            return b.optLong("remoteVersion", 0);
        }
    }
}