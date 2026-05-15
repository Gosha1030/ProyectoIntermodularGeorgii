package georgii.sytnik.thothtasks.domain.place;

import georgii.sytnik.thothtasks.db.AppDatabase;

public final class PlaceService {
    private PlaceService() {
    }

    public static void deletePlaceCascade(AppDatabase db, byte[] placeId) {
        db.taskOverlayDao().clearLocalPlaceEverywhere(placeId);
        db.travelDao().deleteAllUsingPlace(placeId);
        db.placeDao().delete(placeId);
    }
}