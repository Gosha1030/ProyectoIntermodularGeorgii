package georgii.sytnik.thothtasks.net;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

import georgii.sytnik.thothtasks.security.AesGcm;
import georgii.sytnik.thothtasks.security.B64;
import georgii.sytnik.thothtasks.security.HmacAuth;

public final class SecureCodec {

    private SecureCodec() {}

    public static final String MODE_AUTH  = "AUTH";
    public static final String MODE_AEAD  = "AEAD";

    private static byte[] aadOf(JSONObject env) {
        // Bind security to stable header fields
        String s = env.optInt("ver", 1) + "|" +
                env.optString("type", "") + "|" +
                env.optString("msgId", "") + "|" +
                env.optLong("seq", 0) + "|" +
                env.optString("ackOf", "") + "|" +
                env.optString("rid", "");
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static JSONObject wrapAuth(NetSession s, JSONObject envWithBody) {
        try {
            JSONObject sec = new JSONObject();
            sec.put("mode", MODE_AUTH);
            sec.put("sessionId", s.sessionId);

            byte[] aad = aadOf(envWithBody);
            byte[] bodyBytes = envWithBody.getJSONObject("body").toString().getBytes(StandardCharsets.UTF_8);
            byte[] tag = HmacAuth.mac(s.kAuth, concat(aad, bodyBytes));

            sec.put("mac", B64.enc(tag));
            envWithBody.put("sec", sec);
            return envWithBody;
        } catch (Exception e) {
            throw new RuntimeException("wrapAuth failed", e);
        }
    }

    public static JSONObject unwrapAuth(NetSession s, JSONObject env) {
        try {
            JSONObject sec = env.getJSONObject("sec");
            if (!MODE_AUTH.equals(sec.optString("mode"))) throw new RuntimeException("Not AUTH");
            if (!s.sessionId.equals(sec.optString("sessionId"))) throw new RuntimeException("SessionId mismatch");

            byte[] tag = B64.dec(sec.getString("mac"));
            byte[] aad = aadOf(env);
            byte[] bodyBytes = env.getJSONObject("body").toString().getBytes(StandardCharsets.UTF_8);

            if (!HmacAuth.verify(s.kAuth, concat(aad, bodyBytes), tag)) {
                throw new RuntimeException("AUTH mac invalid");
            }
            return env.getJSONObject("body");
        } catch (Exception e) {
            throw new RuntimeException("unwrapAuth failed", e);
        }
    }

    public static JSONObject wrapAead(NetSession s, JSONObject envWithBody) {
        try {
            JSONObject sec = new JSONObject();
            sec.put("mode", MODE_AEAD);
            sec.put("sessionId", s.sessionId);

            long seq = envWithBody.optLong("seq", 0);
            byte[] nonce = nonce12(s.noncePrefix4, seq);
            sec.put("nonce", B64.enc(nonce));

            byte[] aad = aadOf(envWithBody);
            byte[] plain = envWithBody.getJSONObject("body").toString().getBytes(StandardCharsets.UTF_8);
            byte[] cipher = AesGcm.encrypt(s.kAead, nonce, aad, plain);

            sec.put("cipher", B64.enc(cipher));
            envWithBody.remove("body");
            envWithBody.put("sec", sec);
            return envWithBody;
        } catch (Exception e) {
            throw new RuntimeException("wrapAead failed", e);
        }
    }

    public static JSONObject unwrapAead(NetSession s, JSONObject env) {
        try {
            JSONObject sec = env.getJSONObject("sec");
            if (!MODE_AEAD.equals(sec.optString("mode"))) throw new RuntimeException("Not AEAD");
            if (!s.sessionId.equals(sec.optString("sessionId"))) throw new RuntimeException("SessionId mismatch");

            byte[] nonce = B64.dec(sec.getString("nonce"));
            byte[] cipher = B64.dec(sec.getString("cipher"));
            byte[] aad = aadOf(env);

            byte[] plain = AesGcm.decrypt(s.kAead, nonce, aad, cipher);
            return new JSONObject(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("unwrapAead failed", e);
        }
    }

    private static byte[] nonce12(byte[] prefix4, long seq) {
        byte[] n = new byte[12];
        System.arraycopy(prefix4, 0, n, 0, 4);
        for (int i = 0; i < 8; i++) {
            n[11 - i] = (byte) (seq & 0xFF);
            seq >>>= 8;
        }
        return n;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}