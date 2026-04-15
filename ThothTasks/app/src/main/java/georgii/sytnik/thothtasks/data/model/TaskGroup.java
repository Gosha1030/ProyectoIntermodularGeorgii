package georgii.sytnik.thothtasks.data.model;

public class TaskGroup {
    private long taskGroupId;
    private Long parentTaskGroupId;
    private Long userId;
    private String taskGroupName;
    private boolean state;
    private int weight;
    private boolean muted;

    public TaskGroup() {}

    public TaskGroup(long taskGroupId, Long parentTaskGroupId, Long userId, String taskGroupName,
                     boolean state, int weight, boolean muted) {
        this.taskGroupId = taskGroupId;
        this.parentTaskGroupId = parentTaskGroupId;
        this.userId = userId;
        this.taskGroupName = taskGroupName;
        this.state = state;
        this.weight = weight;
        this.muted = muted;
    }

    public long getTaskGroupId() { return taskGroupId; }
    public void setTaskGroupId(long taskGroupId) { this.taskGroupId = taskGroupId; }

    public Long getParentTaskGroupId() { return parentTaskGroupId; }
    public void setParentTaskGroupId(Long parentTaskGroupId) { this.parentTaskGroupId = parentTaskGroupId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTaskGroupName() { return taskGroupName; }
    public void setTaskGroupName(String taskGroupName) { this.taskGroupName = taskGroupName; }

    public boolean isState() { return state; }
    public void setState(boolean state) { this.state = state; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
}