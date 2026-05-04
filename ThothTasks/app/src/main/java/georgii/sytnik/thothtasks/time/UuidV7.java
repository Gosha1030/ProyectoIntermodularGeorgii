package georgii.sytnik.thothtasks.time;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

/**
 * UUIDv7 (time-ordered) generator (millis precision).
 * Layout: 48-bit unix epoch millis + version(7) + random.
 */
public final class UuidV7 {
    private UuidV7() {}

    public static UUID newUuid() {
        return Generators.timeBasedEpochGenerator().generate();
    }
}