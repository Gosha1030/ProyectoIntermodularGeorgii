package georgii.sytnik.thothtasks.ui.action;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import georgii.sytnik.thothtasks.util.HexBytes;

public final class PendingIntentIds {

    private PendingIntentIds() {}

    public static int action(String dayKey, byte[] taskId, String slot, String kind) {
        String s = dayKey + "|" + HexBytes.hex(taskId) + "|" + slot + "|" + kind;
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() & 0x7fffffff);
    }
}
