package georgii.sytnik.thothtasks.data.model;

import georgii.sytnik.thothtasks.data.enumtype.TaskType;

public class Task {
    private long taskId;
    private Long taskGroupId;
    private String taskName;
    private TaskType type;
    private String dateRule;
    private boolean state;
    private String startTime;
    private Integer timeM;
    private Integer periodTimeM;
    private Integer weight;
    private String notifyWhenHow;
    private String whenStart;
    private boolean muted;
    private String where;

    public Task() {}

    public Task(long taskId, Long taskGroupId, String taskName, TaskType type, String dateRule,
                boolean state, String startTime, Integer timeM, Integer periodTimeM,
                Integer weight, String notifyWhenHow, String whenStart, boolean muted, String where) {
        this.taskId = taskId;
        this.taskGroupId = taskGroupId;
        this.taskName = taskName;
        this.type = type;
        this.dateRule = dateRule;
        this.state = state;
        this.startTime = startTime;
        this.timeM = timeM;
        this.periodTimeM = periodTimeM;
        this.weight = weight;
        this.notifyWhenHow = notifyWhenHow;
        this.whenStart = whenStart;
        this.muted = muted;
        this.where = where;
    }

    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }

    public Long getTaskGroupId() { return taskGroupId; }
    public void setTaskGroupId(Long taskGroupId) { this.taskGroupId = taskGroupId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }

    public String getDateRule() { return dateRule; }
    public void setDateRule(String dateRule) { this.dateRule = dateRule; }

    public boolean isState() { return state; }
    public void setState(boolean state) { this.state = state; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public Integer getTimeM() { return timeM; }
    public void setTimeM(Integer timeM) { this.timeM = timeM; }

    public Integer getPeriodTimeM() { return periodTimeM; }
    public void setPeriodTimeM(Integer periodTimeM) { this.periodTimeM = periodTimeM; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }

    public String getNotifyWhenHow() { return notifyWhenHow; }
    public void setNotifyWhenHow(String notifyWhenHow) { this.notifyWhenHow = notifyWhenHow; }

    public String getWhenStart() { return whenStart; }
    public void setWhenStart(String whenStart) { this.whenStart = whenStart; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public String getWhere() { return where; }
    public void setWhere(String where) { this.where = where; }
}