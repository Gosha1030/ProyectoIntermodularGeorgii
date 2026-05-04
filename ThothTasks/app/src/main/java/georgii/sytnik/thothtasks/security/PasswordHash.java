package georgii.sytnik.thothtasks.security;

import android.util.Base64;

import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHash {
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordHash() {}

    public static String hashToStoredString(char[] password) {
        try {
            int iterations = 120_000;
            byte[] salt = new byte[16];
            RNG.nextBytes(salt);

            byte[] hash = pbkdf2(password, salt, iterations, 32);
            String saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP);
            String hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP);

            return "pbkdf2$" + iterations + "$" + saltB64 + "$" + hashB64;
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public static boolean verify(char[] password, String stored) {
        try {
            if (stored == null) return false;
            String[] parts = stored.split("\\$");
            if (parts.length != 4) return false;
            if (!"pbkdf2".equals(parts[0])) return false;

            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.decode(parts[2], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[3], Base64.NO_WRAP);

            byte[] actual = pbkdf2(password, salt, iterations, expected.length);

            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= (a[i] ^ b[i]);
        return r == 0;
    }
}