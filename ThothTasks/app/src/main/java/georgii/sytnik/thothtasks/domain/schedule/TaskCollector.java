package georgii.sytnik.thothtasks.domain.schedule;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public final class TaskCollector {

    private TaskCollector() {
    }

    public static List<TaskWithSource> collect(AppDatabase db, byte[] userTaskRootId) {
        List<TaskWithSource> out = new ArrayList<>();
        collectRec(db, userTaskRootId, null, out);
        return out;
    }

    private static void collectRec(AppDatabase db, byte[] fatherId, byte[] currentSourceId, List<TaskWithSource> out) {
        List<TaskEntity> children = db.taskDao().childrenActiveOf(fatherId);

        for (TaskEntity c : children) {

            if (ScheduleFilters.isEmptyWithoutRestrictions(c)) {
                collectRec(db, c.taskId, currentSourceId, out);
                continue;
            }

            ExternalSourceEntity src = db.externalSourceDao().findByImportedRoot(c.taskId);
            if (src != null) {
                if (!src.includedInSchedule || src.blocked) {
                    continue;
                }
                out.add(new TaskWithSource(c, src.sourceId));
                collectRec(db, c.taskId, src.sourceId, out);
                continue;
            }

            out.add(new TaskWithSource(c, currentSourceId));
            collectRec(db, c.taskId, currentSourceId, out);
        }
    }
}