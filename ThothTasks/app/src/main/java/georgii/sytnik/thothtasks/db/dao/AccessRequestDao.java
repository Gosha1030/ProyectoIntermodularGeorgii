package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.AccessRequestEntity;

@Dao
public interface AccessRequestDao {

    @Query("SELECT * FROM AccessRequest WHERE ExternalUserId = :externalUserId AND ResourceId = :resourceId LIMIT 1")
    AccessRequestEntity find(byte[] externalUserId, byte[] resourceId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AccessRequestEntity e);

    @Query("UPDATE AccessRequest SET State = :state, DecidedAtUtcMs = :decidedAtUtcMs WHERE ExternalUserId = :externalUserId AND ResourceId = :resourceId")
    void setDecision(byte[] externalUserId, byte[] resourceId, String state, long decidedAtUtcMs);

    @Query("DELETE FROM AccessRequest WHERE State != 'PENDING' AND DecidedAtUtcMs IS NOT NULL AND DecidedAtUtcMs < :beforeUtcMs")
    void deleteResolvedBefore(long beforeUtcMs);

    @Query("SELECT * FROM AccessRequest WHERE State = 'PENDING' ORDER BY CreatedAtUtcMs DESC")
    List<AccessRequestEntity> listPending();
}
