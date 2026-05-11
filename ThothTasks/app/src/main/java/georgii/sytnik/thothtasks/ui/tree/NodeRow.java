package georgii.sytnik.thothtasks.ui.tree;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public class NodeRow {
    public final TaskEntity task;
    public final int level;

    public boolean expanded;
    public boolean hasChildren;

    public byte[] sourceId;

    public boolean effectiveMuted;

    public NodeRow(TaskEntity task, int level) {
        this.task = task;
        this.level = level;
        this.sourceId = null;
        this.effectiveMuted = task.muted;
    }
}