package georgii.sytnik.thothtasks.net;

import static georgii.sytnik.thothtasks.util.HexBytes.hex;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.UuidBytes;

public final class MessageCodec {

    private MessageCodec() {
    }

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
        o.put("msgId", hex(UuidBytes.uuidToBytes(UuidV7.newUuid())));
        o.put("senderId", senderIdHex);
        o.put("seq", seq);
        o.put("sentAt", System.currentTimeMillis());
        if (ackOfHex != null) o.put("ackOf", ackOfHex);
        if (body != null) o.put("body", body);
        return o;
    }
}
