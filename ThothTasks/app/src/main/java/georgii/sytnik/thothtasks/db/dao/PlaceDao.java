package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.PlaceEntity;

@Dao
public interface PlaceDao {

    @Query("SELECT * FROM Place ORDER BY PlaceName COLLATE NOCASE")
    List<PlaceEntity> listAll();

    @Query("SELECT * FROM Place WHERE PlaceId = :placeId LIMIT 1")
    PlaceEntity findById(byte[] placeId);

    @Query("SELECT * FROM Place WHERE PlaceName = :name LIMIT 1")
    PlaceEntity findByName(String name);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(PlaceEntity p);

    @Update
    void update(PlaceEntity p);

    @Query("DELETE FROM Place WHERE PlaceId = :placeId")
    void delete(byte[] placeId);
}