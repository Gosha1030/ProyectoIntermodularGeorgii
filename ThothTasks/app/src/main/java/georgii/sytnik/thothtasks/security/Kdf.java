package georgii.sytnik.thothtasks.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Kdf {

    private Kdf() {}

    public static byte[] pbkdf2(
            char[] password,
            byte[] salt,
            int iterations,
            int keyLenBytes
    ) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBytes * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 failed", e);
        }
    }
}