package georgii.sytnik.thothtasks.data.model;

import java.util.ArrayList;
import java.util.List;

public class TaskGroupNode {

    public TaskGroup group;
    public List<TaskGroupNode> children = new ArrayList<>();
    public boolean expanded = true;

    public TaskGroupNode(TaskGroup group) {
        this.group = group;
    }
}
