package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.UserEntity;

@Dao
public interface UserDao {

    @Query("SELECT * FROM User ORDER BY UserName COLLATE NOCASE")
    List<UserEntity> getAllUsers();

    @Query("SELECT * FROM User WHERE UserName = :userName LIMIT 1")
    UserEntity findByUserName(String userName);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(UserEntity user);

    @Update
    void update(UserEntity user);

    @Query("SELECT * FROM User WHERE userId = :userId LIMIT 1")
    UserEntity findById(byte[] userId);
}
