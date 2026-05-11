package georgii.sytnik.thothtasks.domain.place;

import georgii.sytnik.thothtasks.db.AppDatabase;

public final class PlaceService {
    private PlaceService() {}

    public static void deletePlaceCascade(AppDatabase db, byte[] placeId) {
        // 1) tasks locales: placeId -> null
        db.taskDao().clearPlaceForTasks(placeId);

        // 2) overlays: placeLocalId -> null
        db.taskOverlayDao().clearLocalPlaceEverywhere(placeId);

        // 3) travels que lo usan: borrar (porque Start/Finish son NOT NULL)
        db.travelDao().deleteAllUsingPlace(placeId);

        // 4) borrar place
        db.placeDao().delete(placeId);
    }
}