package georgii.sytnik.thothtasks.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;

@Dao
public interface TaskChangeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(TaskChangeEntity change);

    @Query("SELECT * FROM TaskChange WHERE Task = :taskId ORDER BY CreateAt")
    List<TaskChangeEntity> historyForTask(byte[] taskId);

    @Query("SELECT * FROM TaskChange WHERE Task = :taskId AND Type = 'create_task' ORDER BY CreateAt ASC LIMIT 1")
    TaskChangeEntity findCreateTask(byte[] taskId);

    // ✅ FIX: el método que te falta en TaskHierarchyValidator
    @Query(
            "SELECT * FROM TaskChange " +
                    "WHERE Task = :taskId AND Type = 'task_deactivate' " +
                    "AND COALESCE(WhenApply, CreateAt) >= :afterUtcMs " +
                    "ORDER BY COALESCE(WhenApply, CreateAt) ASC LIMIT 1"
    )
    TaskChangeEntity findFirstDeactivateAfter(byte[] taskId, long afterUtcMs);

    // (si lo usas en TaskManager para aplicar cambios vencidos, opcional)
    @Query(
            "SELECT * FROM TaskChange " +
                    "WHERE WhenApply IS NOT NULL AND WhenApply <= :nowUtcMs " +
                    "AND (Type = 'activate' OR Type = 'task_deactivate') " +
                    "ORDER BY WhenApply ASC"
    )
    List<TaskChangeEntity> dueStateChanges(long nowUtcMs);
}

