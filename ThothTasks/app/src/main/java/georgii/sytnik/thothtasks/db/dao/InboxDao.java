package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import georgii.sytnik.thothtasks.db.entities.ReceivedInboxEntity;

@Dao
public interface InboxDao {

    @Query("SELECT COUNT(*) FROM ReceivedInbox WHERE MsgId = :msgId")
    int exists(byte[] msgId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(ReceivedInboxEntity e);

    @Query("DELETE FROM ReceivedInbox WHERE ReceivedAtUtcMs < :olderThanUtcMs")
    void cleanup(long olderThanUtcMs);
}