package georgii.sytnik.thothtasks.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

public final class Ecdh {

    private Ecdh() {
    }

    public static KeyPair generateEphemeral() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("ECDH keypair failed", e);
        }
    }

    public static String pubToB64(PublicKey pub) {
        return B64.enc(pub.getEncoded());
    }

    public static PublicKey pubFromB64(String b64) {
        try {
            byte[] der = B64.dec(b64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("ECDH pub decode failed", e);
        }
    }

    public static byte[] sharedSecret(KeyPair mine, PublicKey theirs) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(mine.getPrivate());
            ka.doPhase(theirs, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new RuntimeException("ECDH shared secret failed", e);
        }
    }
}