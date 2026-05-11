package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.TravelEntity;

@Dao
public interface TravelDao {

    @Query("SELECT * FROM Travel ORDER BY TimeM ASC")
    List<TravelEntity> listAll();

    @Query("SELECT * FROM Travel WHERE TravelId = :travelId LIMIT 1")
    TravelEntity findById(byte[] travelId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(TravelEntity t);

    @Update
    void update(TravelEntity t);

    @Query("DELETE FROM Travel WHERE TravelId = :travelId")
    void delete(byte[] travelId);

    @Query("DELETE FROM Travel WHERE Start = :placeId OR Finish = :placeId")
    void deleteAllUsingPlace(byte[] placeId);

    @Query("SELECT * FROM Travel WHERE Start = :startId AND Finish = :finishId LIMIT 1")
    TravelEntity findByStartFinish(byte[] startId, byte[] finishId);
}