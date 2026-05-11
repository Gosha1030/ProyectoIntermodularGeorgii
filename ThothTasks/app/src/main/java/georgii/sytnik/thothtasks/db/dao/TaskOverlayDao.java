package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;

@Dao
public interface TaskOverlayDao {

    @Query("SELECT * FROM TaskOverlay WHERE SourceId = :sourceId AND TaskId = :taskId LIMIT 1")
    TaskOverlayEntity find(byte[] sourceId, byte[] taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TaskOverlayEntity e);

    @Query("DELETE FROM TaskOverlay WHERE SourceId = :sourceId AND TaskId = :taskId")
    void delete(byte[] sourceId, byte[] taskId);

    @Query("UPDATE TaskOverlay SET PlaceLocalId = NULL WHERE PlaceLocalId = :placeId")
    void clearLocalPlaceEverywhere(byte[] placeId);
}