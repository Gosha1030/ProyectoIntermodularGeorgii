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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(TaskEntity task);

    @Update
    void update(TaskEntity task);

    @Query("SELECT * FROM Task WHERE TaskId = :taskId LIMIT 1")
    TaskEntity findById(byte[] taskId);

    // ✅ FIX: faltaba y lo usa TaskPickerActivity
    @Query("SELECT * FROM Task WHERE TaskFather = :fatherId AND State = 1 ORDER BY TaskName COLLATE NOCASE")
    List<TaskEntity> childrenActiveOf(byte[] fatherId);

    // ✅ FIX: faltaba y lo usa TaskHierarchyValidator
    @Query("SELECT * FROM Task WHERE TaskFather = :fatherId AND NOT (State = 0 AND Muted = 1) ORDER BY TaskName COLLATE NOCASE")
    List<TaskEntity> childrenNotHidden(byte[] fatherId);

    // Opcional: árbol con filtros (si lo usas en TaskManager con toggles)
    @Query(
            "SELECT * FROM Task " +
                    "WHERE TaskFather = :fatherId " +
                    "AND (State = 1 " +
                    "     OR (:includeInactive = 1 AND State = 0 AND Muted = 0) " +
                    "     OR (:includeHidden  = 1 AND State = 0 AND Muted = 1)) " +
                    "ORDER BY TaskName COLLATE NOCASE"
    )
    List<TaskEntity> childrenFiltered(byte[] fatherId, boolean includeInactive, boolean includeHidden);

    @Query(
            "SELECT * FROM Task " +
                    "WHERE TaskName LIKE '%' || :q || '%' " +
                    "AND (State = 1 " +
                    "     OR (:includeInactive = 1 AND State = 0 AND Muted = 0) " +
                    "     OR (:includeHidden  = 1 AND State = 0 AND Muted = 1)) " +
                    "ORDER BY TaskName COLLATE NOCASE"
    )
    List<TaskEntity> searchFilteredByName(String q, boolean includeInactive, boolean includeHidden);

    @Query("UPDATE Task SET Muted = :muted WHERE TaskId = :taskId")
    void setMuted(byte[] taskId, boolean muted);

    @Query("UPDATE Task SET State = :state, Muted = :muted WHERE TaskId = :taskId")
    void setStateMuted(byte[] taskId, boolean state, boolean muted);

    @Query("UPDATE Task SET TaskFather = :newFatherId WHERE TaskFather = :oldFatherId")
    void reparentChildren(byte[] oldFatherId, byte[] newFatherId);

    @Query(
            "WITH RECURSIVE sub(id) AS (" +
                    "  SELECT TaskId FROM Task WHERE TaskId = :rootId " +
                    "  UNION ALL " +
                    "  SELECT Task.TaskId FROM Task JOIN sub ON Task.TaskFather = sub.id" +
                    ") " +
                    "UPDATE Task SET State = 0, Muted = 1 WHERE TaskId IN (SELECT id FROM sub)"
    )
    void hideSubtree(byte[] rootId);
}