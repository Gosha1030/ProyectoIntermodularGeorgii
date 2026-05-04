package georgii.sytnik.thothtasks.domain.validation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.domain.schedule.OccurrenceEngine;
import georgii.sytnik.thothtasks.domain.schedule.ScheduleHorizon;

public final class TaskHierarchyValidator {

    public static class ValidationResult {
        public final boolean ok;
        public final String message;
        private ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String m) { return new ValidationResult(false, m); }
    }

    private TaskHierarchyValidator() {}

    /**
     * Validate a CHILD candidate under a chosen parentId:
     * Child must fit at least once inside parent's sub-schedule.
     *
     * @param childStartUtcMs start of child activation (for new candidate). If null -> now.
     */
    public static ValidationResult canChildExistInsideParent(
            AppDatabase db,
            TaskEntity childCandidate,
            Long childStartUtcMs,
            byte[] parentId
    ) {
        TaskEntity parent = db.taskDao().findById(parentId);
        if (parent == null) return ValidationResult.ok();

        if (isRoot(parent)) return ValidationResult.ok();

        // Effective parent: skip Empty without restrictions (transparent) and also skip any parent without restrictions
        TaskEntity effectiveParent = findEffectiveParent(db, parent);
        if (effectiveParent == null) return ValidationResult.ok();

        TaskChangeEntity create = db.taskChangeDao().findCreateTask(effectiveParent.taskId);
        if (create == null) {
            // If missing, fallback to now (should not happen in normal flow)
            return ValidationResult.ok();
        }
        long parentStartUtc = (create.whenApplyUtcMs != null) ? create.whenApplyUtcMs : create.createAtUtcMs;

        // Deactivation schedule: first task_deactivate after parentStart
        TaskChangeEntity deact = db.taskChangeDao().findFirstDeactivateAfter(effectiveParent.taskId, parentStartUtc);
        Long parentDeactivateUtc = (deact != null)
                ? (deact.whenApplyUtcMs != null ? deact.whenApplyUtcMs : deact.createAtUtcMs)
                : null;

        // Empty only generates sub-schedule if it has restrictions; if it doesn't, it should have been skipped.
        long endUtc = ScheduleHorizon.computeEndUtc(effectiveParent, parentStartUtc, parentDeactivateUtc);

        long childStartUtc = (childStartUtcMs != null) ? childStartUtcMs : System.currentTimeMillis();
        long startUtc = Math.max(parentStartUtc, childStartUtc);

        if (endUtc < startUtc) {
            return ValidationResult.fail("El padre no está activo en el rango de chequeo.");
        }

        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(startUtc);
        zeroTime(day);

        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(endUtc);
        zeroTime(end);

        while (!day.after(end)) {
            if (OccurrenceEngine.isActiveOnDay(effectiveParent, parentStartUtc, day)
                    && OccurrenceEngine.isActiveOnDay(childCandidate, childStartUtc, day)
                    && timeCompatible(effectiveParent, childCandidate)) {
                return ValidationResult.ok();
            }
            day.add(Calendar.DATE, 1);
        }

        return ValidationResult.fail("La tarea hija no encaja en ningún sub-horario válido del padre.");
    }

    /**
     * Validate when you EDIT a parent: all “layer tasks” under oldParentId must still fit at least once
     * inside the NEW parent candidate's sub-schedule.
     *
     * Layer tasks collection ignores Empty without restrictions (transparent) and dives into its children.
     */
    public static ValidationResult canLayerDescendantsStillFitInsideNewParent(
            AppDatabase db,
            byte[] oldParentId,
            TaskEntity newParentCandidate,
            long newParentStartUtcMs,
            Long newParentDeactivateUtcMs
    ) {
        if (isRootCandidate(newParentCandidate)) return ValidationResult.ok();

        // If newParent is Empty without restrictions, it is transparent -> no restriction
        if (isEmptyWithoutRestrictions(newParentCandidate)) return ValidationResult.ok();

        long endUtc = ScheduleHorizon.computeEndUtc(newParentCandidate, newParentStartUtcMs, newParentDeactivateUtcMs);

        List<TaskEntity> layerTasks = new ArrayList<>();
        collectLayerTasks(db, oldParentId, layerTasks);

        Calendar dayStart = Calendar.getInstance();
        Calendar dayEnd = Calendar.getInstance();
        dayStart.setTimeInMillis(newParentStartUtcMs);
        dayEnd.setTimeInMillis(endUtc);
        zeroTime(dayStart);
        zeroTime(dayEnd);

        for (TaskEntity t : layerTasks) {
            // hidden tasks are excluded in collectLayerTasks, so no need to check
            Long childStart = resolveTaskStartUtc(db, t.taskId);
            if (childStart == null) childStart = System.currentTimeMillis();

            long startUtc = Math.max(newParentStartUtcMs, childStart);

            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(startUtc);
            zeroTime(day);

            boolean ok = false;
            while (!day.after(dayEnd)) {
                if (OccurrenceEngine.isActiveOnDay(newParentCandidate, newParentStartUtcMs, day)
                        && OccurrenceEngine.isActiveOnDay(t, childStart, day)
                        && timeCompatible(newParentCandidate, t)) {
                    ok = true;
                    break;
                }
                day.add(Calendar.DATE, 1);
            }

            if (!ok) {
                return ValidationResult.fail("El hijo '" + t.taskName + "' dejaría de encajar en el padre.");
            }
        }

        return ValidationResult.ok();
    }

    // ----------------- time compatibility (your rule) -----------------

    private static boolean timeCompatible(TaskEntity parent, TaskEntity child) {
        if (parent.startTimeMin == null || parent.finishTimeMin == null) return true;

        int parentLen = parent.finishTimeMin - parent.startTimeMin;
        if (parentLen <= 0) return false;

        // child may omit start/finish if it has TimeM and fits
        if (child.startTimeMin == null && child.finishTimeMin == null) {
            return child.timeM != null && child.timeM <= parentLen;
        }

        // otherwise must have full window and fit inside
        if (child.startTimeMin == null || child.finishTimeMin == null) return false;

        return parent.startTimeMin <= child.startTimeMin
                && child.startTimeMin < child.finishTimeMin
                && child.finishTimeMin <= parent.finishTimeMin;
    }

    // ----------------- effective parent logic (transparent Empty) -----------------

    private static TaskEntity findEffectiveParent(AppDatabase db, TaskEntity start) {
        TaskEntity cur = start;
        while (cur != null) {
            if (isRoot(cur)) return null;

            if (hasRestrictions(cur)) return cur;

            // transparent -> climb
            if (cur.taskFather == null) return null;
            cur = db.taskDao().findById(cur.taskFather);
        }
        return null;
    }

    private static boolean hasRestrictions(TaskEntity t) {
        // For Empty, restrictions are only relevant if present (otherwise transparent)
        return t.startTimeMin != null
                || t.finishTimeMin != null
                || t.timeM != null
                || (t.daysOfJson != null && !t.daysOfJson.trim().isEmpty())
                || (t.periodicJson != null && !t.periodicJson.trim().isEmpty())
                || t.periodD != null;
    }

    private static boolean isEmptyWithoutRestrictions(TaskEntity t) {
        return "Empty".equals(t.type) && !hasRestrictions(t);
    }

    private static boolean isRoot(TaskEntity t) {
        return "Empty".equals(t.type) && t.taskFather == null;
    }

    private static boolean isRootCandidate(TaskEntity t) {
        // candidate root detection is structural: Empty + no father
        return "Empty".equals(t.type) && t.taskFather == null;
    }

    private static void collectLayerTasks(AppDatabase db, byte[] parentId, List<TaskEntity> out) {
        List<TaskEntity> children = db.taskDao().childrenNotHidden(parentId);

        for (TaskEntity c : children) {
            // ignore hidden = filtered already
            if (isEmptyWithoutRestrictions(c)) {
                // transparent empty -> go deeper
                collectLayerTasks(db, c.taskId, out);
            } else {
                out.add(c);
            }
        }
    }

    private static Long resolveTaskStartUtc(AppDatabase db, byte[] taskId) {
        TaskChangeEntity create = db.taskChangeDao().findCreateTask(taskId);
        if (create == null) return null;
        return (create.whenApplyUtcMs != null) ? create.whenApplyUtcMs : create.createAtUtcMs;
    }

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}