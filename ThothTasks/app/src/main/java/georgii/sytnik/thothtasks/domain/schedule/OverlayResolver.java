package georgii.sytnik.thothtasks.domain.schedule;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;

public final class OverlayResolver {

    private OverlayResolver() {}

    public static boolean effectiveMuted(AppDatabase db, byte[] sourceIdOrNull, byte[] taskId, boolean baseMuted) {
        if (sourceIdOrNull == null) return baseMuted;

        TaskOverlayEntity ov = db.taskOverlayDao().find(sourceIdOrNull, taskId);
        if (ov != null && ov.mutedLocal != null) return ov.mutedLocal;
        return baseMuted;
    }
}