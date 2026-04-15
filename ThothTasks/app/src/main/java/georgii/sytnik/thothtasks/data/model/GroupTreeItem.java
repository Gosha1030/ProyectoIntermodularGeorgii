package georgii.sytnik.thothtasks.data.model;

public class GroupTreeItem {

    private final TaskGroup group;
    private final int level;
    private final boolean hasChildren;

    public GroupTreeItem(TaskGroup group, int level, boolean hasChildren) {
        this.group = group;
        this.level = level;
        this.hasChildren = hasChildren;
    }

    public TaskGroup getGroup() {
        return group;
    }

    public int getLevel() {
        return level;
    }

    public boolean hasChildren() {
        return hasChildren;
    }
}
