package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.RemoteTaskMapEntity;

@Dao
public interface RemoteTaskMapDao {

    @Query("SELECT * FROM RemoteTaskMap WHERE SourceId = :sourceId")
    List<RemoteTaskMapEntity> listForSource(byte[] sourceId);

    @Query("DELETE FROM RemoteTaskMap WHERE SourceId = :sourceId")
    void deleteForSource(byte[] sourceId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RemoteTaskMapEntity e);
}