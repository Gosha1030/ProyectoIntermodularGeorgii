package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.SyncStateEntity;

@Dao
public interface SyncStateDao {

    @Query("SELECT * FROM SyncState WHERE PeerKey = :peerKey")
    List<SyncStateEntity> listForPeer(String peerKey);

    @Query("SELECT * FROM SyncState WHERE PeerKey = :peerKey AND ResourceId = :resourceId LIMIT 1")
    SyncStateEntity find(String peerKey, byte[] resourceId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncStateEntity e);

    @Update
    void update(SyncStateEntity e);

    @Query("DELETE FROM SyncState WHERE SyncId = :syncId")
    void delete(byte[] syncId);
}