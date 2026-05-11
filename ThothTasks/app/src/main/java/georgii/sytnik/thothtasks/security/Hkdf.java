package georgii.sytnik.thothtasks.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

public final class Hkdf {

    private Hkdf() {}

    public static byte[] extract(byte[] salt, byte[] ikm) {
        return hmac(salt, ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int outLen) {
        byte[] out = new byte[outLen];
        byte[] t = new byte[0];
        int pos = 0;
        byte counter = 1;

        while (pos < outLen) {
            byte[] data = concat(t, info, new byte[]{counter});
            t = hmac(prk, data);
            int copy = Math.min(t.length, outLen - pos);
            System.arraycopy(t, 0, out, pos, copy);
            pos += copy;
            counter++;
        }
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HKDF HMAC failed", e);
        }
    }

    private static byte[] concat(byte[]... arrs) {
        int len = 0;
        for (byte[] a : arrs) len += a.length;
        byte[] r = new byte[len];
        int p = 0;
        for (byte[] a : arrs) {
            System.arraycopy(a, 0, r, p, a.length);
            p += a.length;
        }
        return r;
    }
}
