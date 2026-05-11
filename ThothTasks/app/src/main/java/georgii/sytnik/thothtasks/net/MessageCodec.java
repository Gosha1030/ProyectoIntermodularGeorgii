package georgii.sytnik.thothtasks.net;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import georgii.sytnik.thothtasks.time.UuidV7;

public final class MessageCodec {

    private MessageCodec() {}

    public static byte[] encode(JSONObject envelope) {
        return envelope.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static JSONObject decode(byte[] data, int len) throws Exception {
        String s = new String(data, 0, len, StandardCharsets.UTF_8);
        return new JSONObject(s);
    }

    public static JSONObject envelope(String type, String senderIdHex, long seq, String ackOfHex, JSONObject body) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("ver", Protocol.VER);
        o.put("type", type);
        o.put("msgId", hex(uuidToBytes(UuidV7.newUuid())));
        o.put("senderId", senderIdHex);
        o.put("seq", seq);
        o.put("sentAt", System.currentTimeMillis());
        if (ackOfHex != null) o.put("ackOf", ackOfHex);
        if (body != null) o.put("body", body);
        return o;
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[] {
                (byte)(msb >>> 56), (byte)(msb >>> 48), (byte)(msb >>> 40), (byte)(msb >>> 32),
                (byte)(msb >>> 24), (byte)(msb >>> 16), (byte)(msb >>>  8), (byte)(msb),
                (byte)(lsb >>> 56), (byte)(lsb >>> 48), (byte)(lsb >>> 40), (byte)(lsb >>> 32),
                (byte)(lsb >>> 24), (byte)(lsb >>> 16), (byte)(lsb >>>  8), (byte)(lsb)
        };
    }
}
