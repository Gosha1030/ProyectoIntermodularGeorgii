package georgii.sytnik.thothtasks.domain;

import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public final class TaskChangeApplier {

    private TaskChangeApplier() {}

    public static void applyDueStateChanges(AppDatabase db, long nowUtcMs) {
        List<TaskChangeEntity> due = db.taskChangeDao().dueStateChanges(nowUtcMs);
        if (due == null || due.isEmpty()) return;

        for (TaskChangeEntity ch : due) {
            TaskEntity t = db.taskDao().findById(ch.taskId);
            if (t == null) continue;

            // Si está oculto (State=false && Muted=true) no lo tocamos
            if (!t.state && t.muted) continue;

            if ("activate".equals(ch.type)) {
                if (!t.state) {
                    db.taskDao().setStateMuted(t.taskId, true, t.muted);
                }
            } else if ("task_deactivate".equals(ch.type)) {
                // tu regla: desactivar => Muted=false
                if (t.state || t.muted) {
                    db.taskDao().setStateMuted(t.taskId, false, false);
                }
            }
        }
    }
}