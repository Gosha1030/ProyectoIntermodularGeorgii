package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import georgii.sytnik.thothtasks.db.entities.ImportedSourceStateEntity;

@Dao
public interface ImportedSourceStateDao {

    @Query("SELECT * FROM ImportedSourceState WHERE SourceId = :sourceId LIMIT 1")
    ImportedSourceStateEntity find(byte[] sourceId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ImportedSourceStateEntity e);

    @Query("DELETE FROM ImportedSourceState WHERE SourceId = :sourceId")
    void delete(byte[] sourceId);
}