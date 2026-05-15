package georgii.sytnik.thothtasks.security;

import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacAuth {

    private HmacAuth() {
    }

    public static byte[] mac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }

    public static boolean verify(byte[] key, byte[] data, byte[] tag) {
        byte[] m = mac(key, data);
        return MessageDigest.isEqual(m, tag);
    }
}