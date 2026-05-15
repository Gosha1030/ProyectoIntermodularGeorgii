package georgii.sytnik.thothtasks.time;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

public final class UuidV7 {
    private UuidV7() {
    }

    public static UUID newUuid() {
        return Generators.timeBasedEpochGenerator().generate();
    }
}