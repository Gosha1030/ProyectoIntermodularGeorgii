package georgii.sytnik.thothtasks.domain.place;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;

public final class PlaceResolver {

    private PlaceResolver() {
    }

    public static byte[] effectivePlaceId(AppDatabase db, byte[] sourceIdOrNull, TaskEntity t) {
        if (sourceIdOrNull != null) {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceIdOrNull, t.taskId);
            if (ov != null && ov.placeLocalId != null) return ov.placeLocalId;
        }

        if (t.placeId != null) return t.placeId;

        byte[] curFather = t.taskFather;
        while (curFather != null) {
            TaskEntity p = db.taskDao().findById(curFather);
            if (p == null) break;
            if (p.placeId != null) return p.placeId;
            curFather = p.taskFather;
        }

        return null;
    }

    public static String placeNameOrAny(AppDatabase db, byte[] placeId) {
        if (placeId == null) return "(Cualquier lugar)";
        PlaceEntity p = db.placeDao().findById(placeId);
        return p != null ? p.placeName : "(?)";
    }
}