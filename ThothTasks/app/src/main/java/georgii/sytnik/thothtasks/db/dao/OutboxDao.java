package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.PendingOutboxEntity;

@Dao
public interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PendingOutboxEntity e);

    @Query("SELECT * FROM PendingOutbox WHERE NextRetryUtcMs <= :nowUtcMs ORDER BY NextRetryUtcMs ASC LIMIT :limit")
    List<PendingOutboxEntity> due(long nowUtcMs, int limit);

    @Query("DELETE FROM PendingOutbox WHERE MsgId = :msgId")
    void delete(byte[] msgId);
}