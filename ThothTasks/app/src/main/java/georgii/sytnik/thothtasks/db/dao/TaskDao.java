package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

@Dao
public interface TaskDao {

    @Query("SELECT * FROM Task WHERE TaskId = :taskId LIMIT 1")
    TaskEntity findById(byte[] taskId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(TaskEntity t);

    @Update
    void update(TaskEntity t);

    @Query("UPDATE Task SET Muted = :muted WHERE TaskId = :taskId")
    void setMuted(byte[] taskId, boolean muted);

    @Query("UPDATE Task SET State = :state, Muted = :muted WHERE TaskId = :taskId")
    void setStateMuted(byte[] taskId, boolean state, boolean muted);

    @Query("UPDATE Task SET 'Action' = :actionJson WHERE TaskId = :taskId")
    void setActionJson(byte[] taskId, String actionJson);

    @Query("UPDATE Task SET Place = NULL WHERE Place = :placeId")
    void clearPlaceForTasks(byte[] placeId);

    @Query("UPDATE Task SET TaskFather = :newFatherId WHERE TaskFather = :oldFatherId")
    void reparentChildren(byte[] oldFatherId, byte[] newFatherId);

    @Query("UPDATE Task SET State = 0, Muted = 1 WHERE TaskId = :rootId OR TaskFather = :rootId")
    void hideSubtree(byte[] rootId);

    @Query("SELECT * FROM Task WHERE TaskFather = :fatherId AND State = 1 ORDER BY TaskName COLLATE NOCASE")
    List<TaskEntity> childrenActiveOf(byte[] fatherId);

    @Query("SELECT * FROM Task WHERE TaskFather = :fatherId AND NOT (State = 0 AND Muted = 1) ORDER BY TaskName COLLATE NOCASE")
    List<TaskEntity> childrenNotHidden(byte[] fatherId);

    @Query("SELECT * FROM Task " + "WHERE TaskFather = :fatherId " + "AND (State = 1 " + "     OR (:includeInactive = 1 AND State = 0 AND Muted = 0) " + "     OR (:includeHidden  = 1 AND State = 0 AND Muted = 1)) " + "ORDER BY TaskName COLLATE NOCASE")
    List<TaskEntity> childrenFiltered(byte[] fatherId, boolean includeInactive, boolean includeHidden);

    @Query("SELECT * FROM Task " + "WHERE TaskName LIKE '%' || :q || '%' " + "AND (State = 1 " + "     OR (:includeInactive = 1 AND State = 0 AND Muted = 0) " + "     OR (:includeHidden  = 1 AND State = 0 AND Muted = 1)) " + "ORDER BY TaskName COLLATE NOCASE")
    List<TaskEntity> searchFilteredByName(String q, boolean includeInactive, boolean includeHidden);
}
