package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;

@Dao
public interface ShareResourceDao {

    @Query("SELECT * FROM ShareResource WHERE OwnerUserId = :ownerUserId ORDER BY Name COLLATE NOCASE")
    List<ShareResourceEntity> listForOwner(byte[] ownerUserId);

    @Query("SELECT * FROM ShareResource WHERE ResourceId = :resourceId LIMIT 1")
    ShareResourceEntity findById(byte[] resourceId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ShareResourceEntity e);

    @Update
    void update(ShareResourceEntity e);

    @Query("DELETE FROM ShareResource WHERE ResourceId = :resourceId")
    void delete(byte[] resourceId);
}