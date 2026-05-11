package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;

@Dao
public interface ExternalSourceDao {

    @Query("SELECT * FROM ExternalSource ORDER BY DisplayName COLLATE NOCASE")
    List<ExternalSourceEntity> listAll();

    @Query("SELECT * FROM ExternalSource WHERE Blocked = 0 AND IncludedInSchedule = 1 AND ImportedRootTaskId IS NOT NULL")
    List<ExternalSourceEntity> listIncludedReady();

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ExternalSourceEntity e);

    @Query("DELETE FROM ExternalSource WHERE SourceId = :sourceId")
    void delete(byte[] sourceId);

    @Query("UPDATE ExternalSource SET IncludedInSchedule = :included WHERE SourceId = :sourceId")
    void setIncluded(byte[] sourceId, boolean included);

    @Query("UPDATE ExternalSource SET ImportedRootTaskId = :rootTaskId WHERE SourceId = :sourceId")
    void setImportedRoot(byte[] sourceId, byte[] rootTaskId);

    @Query("SELECT * FROM ExternalSource WHERE ImportedRootTaskId IS NOT NULL")
    List<ExternalSourceEntity> listAllWithImportedRoot();

    @Query("SELECT * FROM ExternalSource WHERE ImportedRootTaskId = :rootTaskId LIMIT 1")
    ExternalSourceEntity findByImportedRoot(byte[] rootTaskId);
}