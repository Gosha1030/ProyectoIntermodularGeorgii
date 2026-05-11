package georgii.sytnik.thothtasks.domain.schedule;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public class TaskWithSource {
    public final TaskEntity task;
    public final byte[] sourceId; // null si es local

    public TaskWithSource(TaskEntity task, byte[] sourceId) {
        this.task = task;
        this.sourceId = sourceId;
    }
}