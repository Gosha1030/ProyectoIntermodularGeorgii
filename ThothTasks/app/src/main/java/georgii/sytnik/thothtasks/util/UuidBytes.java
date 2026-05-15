package georgii.sytnik.thothtasks.util;

import java.util.UUID;

public final class UuidBytes {
    private UuidBytes() {
    }

    public static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[]{
                (byte) (msb >>> 56), (byte) (msb >>> 48), (byte) (msb >>> 40), (byte) (msb >>> 32),
                (byte) (msb >>> 24), (byte) (msb >>> 16), (byte) (msb >>> 8), (byte) (msb),
                (byte) (lsb >>> 56), (byte) (lsb >>> 48), (byte) (lsb >>> 40), (byte) (lsb >>> 32),
                (byte) (lsb >>> 24), (byte) (lsb >>> 16), (byte) (lsb >>> 8), (byte) (lsb)
        };
    }
}
