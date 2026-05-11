package georgii.sytnik.thothtasks.domain.place;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;

public final class PlaceResolver {

    private PlaceResolver() {}

    /** Returns placeId effective for this task node (no travel logic yet). */
    public static byte[] effectivePlaceId(AppDatabase db, byte[] sourceIdOrNull, TaskEntity t) {
        // imported overlay
        if (sourceIdOrNull != null) {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceIdOrNull, t.taskId);
            if (ov != null && ov.placeLocalId != null) return ov.placeLocalId;
        }

        // local explicit
        if (t.placeId != null) return t.placeId;

        // dynamic parent inheritance (walk up)
        byte[] curFather = t.taskFather;
        while (curFather != null) {
            TaskEntity p = db.taskDao().findById(curFather);
            if (p == null) break;
            if (p.placeId != null) return p.placeId;
            curFather = p.taskFather;
        }

        return null; // "anywhere"
    }

    public static String placeNameOrAny(AppDatabase db, byte[] placeId) {
        if (placeId == null) return "(Cualquier lugar)";
        PlaceEntity p = db.placeDao().findById(placeId);
        return p != null ? p.placeName : "(?)";
    }
}