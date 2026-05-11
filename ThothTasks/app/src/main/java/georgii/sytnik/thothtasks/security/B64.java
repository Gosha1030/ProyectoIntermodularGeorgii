package georgii.sytnik.thothtasks.security;

import android.util.Base64;

public final class B64 {
    private B64() {}

    public static String enc(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] dec(String b64) {
        return Base64.decode(b64, Base64.NO_WRAP);
    }
}