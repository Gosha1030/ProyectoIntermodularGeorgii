package georgii.sytnik.thothtasks.domain.schedule;

import static georgii.sytnik.thothtasks.util.HexBytes.hex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.ui.schedule.DayBlock;

public final class DayTimelinePlanner {

    private DayTimelinePlanner() {
    }

    /**
     * Build visible blocks for one day
     */
    public static List<DayBlock> buildBlocks(List<TaskEntity> tasks, Map<String, Integer> depthById) {
        int[] visibleIdx = new int[1440];
        for (int i = 0; i < 1440; i++) visibleIdx[i] = -1;

        for (int minute = 0; minute < 1440; minute++) {
            int best = -1;
            int bestDepth = -1;
            int bestWeight = Integer.MIN_VALUE;

            for (int i = 0; i < tasks.size(); i++) {
                TaskEntity t = tasks.get(i);

                if (t.startTimeMin == null || t.finishTimeMin == null) continue;
                int s = t.startTimeMin;
                int e = t.finishTimeMin;
                if (!(s <= minute && minute < e)) continue;

                int depth = depthById.getOrDefault(hex(t.taskId), 1);
                int w = (t.weight != null) ? t.weight : 0;

                if (depth > bestDepth || (depth == bestDepth && w > bestWeight)) {
                    best = i;
                    bestDepth = depth;
                    bestWeight = w;
                }
            }

            visibleIdx[minute] = best;
        }

        List<DayBlock> blocks = new ArrayList<>();
        int curIdx = visibleIdx[0];
        int curStart = 0;

        for (int m = 1; m <= 1440; m++) {
            int idx = (m == 1440) ? Integer.MIN_VALUE : visibleIdx[m];
            if (idx != curIdx) {
                if (curIdx != -1) {
                    TaskEntity t = tasks.get(curIdx);
                    blocks.add(new DayBlock(
                            hex(t.taskId),
                            t.taskName,
                            curStart,
                            m,
                            t.muted
                    ));

                }
                curIdx = idx;
                curStart = m;
            }
        }

        return blocks;
    }

    public static Map<String, Integer> computeDepths(List<TaskEntity> tasks) {
        Map<String, TaskEntity> byId = new HashMap<>();
        for (TaskEntity t : tasks) byId.put(hex(t.taskId), t);

        Map<String, Integer> depth = new HashMap<>();

        for (TaskEntity t : tasks) {
            depth.put(hex(t.taskId), computeDepthFor(t, byId, depth));
        }
        return depth;
    }

    private static int computeDepthFor(TaskEntity t, Map<String, TaskEntity> byId, Map<String, Integer> memo) {
        String id = hex(t.taskId);
        if (memo.containsKey(id)) return memo.get(id);

        if (t.taskFather == null) {
            memo.put(id, 1);
            return 1;
        }

        TaskEntity parent = byId.get(hex(t.taskFather));
        if (parent == null) {
            memo.put(id, 1);
            return 1;
        }

        int d = computeDepthFor(parent, byId, memo) + 1;
        memo.put(id, d);
        return d;
    }
}
