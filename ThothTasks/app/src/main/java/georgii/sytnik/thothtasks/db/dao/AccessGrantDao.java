package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.AccessGrantEntity;

@Dao
public interface AccessGrantDao {

    @Query("SELECT * FROM AccessGrant WHERE ExternalUserId = :externalUserId")
    List<AccessGrantEntity> grantsForExternal(byte[] externalUserId);

    @Query("SELECT * FROM AccessGrant WHERE ExternalUserId = :externalUserId AND ResourceId = :resourceId LIMIT 1")
    AccessGrantEntity find(byte[] externalUserId, byte[] resourceId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AccessGrantEntity e);

    @Update
    void update(AccessGrantEntity e);

    @Query("DELETE FROM AccessGrant WHERE GrantId = :grantId")
    void delete(byte[] grantId);
}