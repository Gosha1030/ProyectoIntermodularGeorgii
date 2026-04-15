package georgii.sytnik.thothtasks.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import georgii.sytnik.thothtasks.data.model.TaskGroup;
import georgii.sytnik.thothtasks.data.model.TaskGroupNode;

public class TaskGroupTreeBuilder {

    public static List<TaskGroupNode> build(List<TaskGroup> groups) {
        Map<Long, TaskGroupNode> map = new HashMap<>();
        List<TaskGroupNode> roots = new ArrayList<>();

        for (TaskGroup g : groups) {
            map.put(g.getTaskGroupId(), new TaskGroupNode(g));
        }

        for (TaskGroup g : groups) {
            TaskGroupNode node = map.get(g.getTaskGroupId());
            if (g.getParentTaskGroupId() == null) {
                roots.add(node);
            } else {
                TaskGroupNode parent = map.get(g.getParentTaskGroupId());
                if (parent != null) {
                    parent.children.add(node);
                } else {
                    roots.add(node);
                }
            }
        }

        return roots;
    }
}