package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;

@Dao
public interface ExternalUserDao {

    @Query("SELECT * FROM ExternalUser WHERE User = :ownerUserId ORDER BY ExternalUserName COLLATE NOCASE")
    List<ExternalUserEntity> listForOwner(byte[] ownerUserId);

    @Update
    void update(ExternalUserEntity e);

    @Query("UPDATE ExternalUser SET Blocked = :blocked WHERE ExternalId = :externalId")
    void setBlocked(byte[] externalId, boolean blocked);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ExternalUserEntity e);

    @Query("SELECT * FROM ExternalUser WHERE User = :ownerUserId AND Ip = :ip AND Port = :port LIMIT 1")
    ExternalUserEntity findByIpPort(byte[] ownerUserId, String ip, int port);
}
