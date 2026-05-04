package georgii.sytnik.thothtasks.ui.schedule;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public class ScheduleTaskRow {
    public final TaskEntity task;
    public final int sortKey; // minutes start if exists else big
    public final String timeText;
    public final String subText;

    public ScheduleTaskRow(TaskEntity task, int sortKey, String timeText, String subText) {
        this.task = task;
        this.sortKey = sortKey;
        this.timeText = timeText;
        this.subText = subText;
    }
}