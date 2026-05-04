package georgii.sytnik.thothtasks.db;

import androidx.room.TypeConverter;

import java.nio.ByteBuffer;
import java.util.UUID;

public class Converters {

    @TypeConverter
    public static byte[] uuidToBytes(UUID uuid) {
        if (uuid == null) return null;
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    @TypeConverter
    public static UUID bytesToUuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) return null;
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low);
    }
}