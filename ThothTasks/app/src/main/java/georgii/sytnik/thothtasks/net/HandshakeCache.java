package georgii.sytnik.thothtasks.net;

import java.security.KeyPair;

public final class HandshakeCache {

    private static final java.util.concurrent.ConcurrentHashMap<String, Pending> MAP = new java.util.concurrent.ConcurrentHashMap<>();

    private HandshakeCache() {
    }

    public static void put(String key, Pending p) {
        MAP.put(key, p);
    }

    public static Pending get(String key) {
        return MAP.get(key);
    }

    public static void remove(String key) {
        MAP.remove(key);
    }

    public static void cleanupOld(long nowUtcMs) {
        for (var e : MAP.entrySet()) {
            if (nowUtcMs - e.getValue().createdAtUtcMs > 60_000) {
                MAP.remove(e.getKey());
            }
        }
    }

    public static class Pending {
        public long createdAtUtcMs;
        public String ridHex;
        public String peerKey;
        public byte[] nonceA;
        public byte[] nonceB;
        public String ephPubA_B64;
        public KeyPair ephB;
        public byte[] salt;
        public int iters;
    }
}