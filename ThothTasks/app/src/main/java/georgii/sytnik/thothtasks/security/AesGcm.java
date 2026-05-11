package georgii.sytnik.thothtasks.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcm {

    private AesGcm() {}

    public static byte[] encrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] plaintext) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, nonce12);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key32, "AES"), spec);
            if (aad != null) c.updateAAD(aad);
            return c.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] cipherText) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, nonce12);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key32, "AES"), spec);
            if (aad != null) c.updateAAD(aad);
            return c.doFinal(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM decrypt failed", e);
        }
    }
}