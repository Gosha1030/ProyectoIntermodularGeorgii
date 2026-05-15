package georgii.sytnik.thothtasks.domain.validation;

import static georgii.sytnik.thothtasks.util.TimeText.zeroTime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.domain.schedule.OccurrenceEngine;
import georgii.sytnik.thothtasks.domain.schedule.ScheduleHorizon;

public final class TaskHierarchyValidator {

    private TaskHierarchyValidator() {
    }

    public static ValidationResult canChildExistInsideParent(
            AppDatabase db,
            TaskEntity childCandidate,
            Long childStartUtcMs,
            byte[] parentId
    ) {
        TaskEntity parent = db.taskDao().findById(parentId);
        if (parent == null) return ValidationResult.ok();

        if (isRoot(parent)) return ValidationResult.ok();

        TaskEntity effectiveParent = findEffectiveParent(db, parent);
        if (effectiveParent == null) return ValidationResult.ok();

        TaskChangeEntity create = db.taskChangeDao().findCreateTask(effectiveParent.taskId);
        if (create == null) {
            return ValidationResult.ok();
        }
        long parentStartUtc = (create.whenApplyUtcMs != null) ? create.whenApplyUtcMs : create.createAtUtcMs;

        TaskChangeEntity deact = db.taskChangeDao().findFirstDeactivateAfter(effectiveParent.taskId, parentStartUtc);
        Long parentDeactivateUtc = (deact != null)
                ? (deact.whenApplyUtcMs != null ? deact.whenApplyUtcMs : deact.createAtUtcMs)
                : null;

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

    private static boolean timeCompatible(TaskEntity parent, TaskEntity child) {
        if (parent.startTimeMin == null || parent.finishTimeMin == null) return true;

        int parentLen = parent.finishTimeMin - parent.startTimeMin;
        if (parentLen <= 0) return false;

        if (child.startTimeMin == null && child.finishTimeMin == null) {
            return child.timeM != null && child.timeM <= parentLen;
        }

        if (child.startTimeMin == null || child.finishTimeMin == null) return false;

        return parent.startTimeMin <= child.startTimeMin
                && child.startTimeMin < child.finishTimeMin
                && child.finishTimeMin <= parent.finishTimeMin;
    }

    private static TaskEntity findEffectiveParent(AppDatabase db, TaskEntity start) {
        TaskEntity cur = start;
        while (cur != null) {
            if (isRoot(cur)) return null;

            if (hasRestrictions(cur)) return cur;

            if (cur.taskFather == null) return null;
            cur = db.taskDao().findById(cur.taskFather);
        }
        return null;
    }

    private static boolean hasRestrictions(TaskEntity t) {
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

    private static void collectLayerTasks(AppDatabase db, byte[] parentId, List<TaskEntity> out) {
        List<TaskEntity> children = db.taskDao().childrenNotHidden(parentId);

        for (TaskEntity c : children) {
            if (isEmptyWithoutRestrictions(c)) {
                collectLayerTasks(db, c.taskId, out);
            } else {
                out.add(c);
            }
        }
    }

    public static class ValidationResult {
        public final boolean ok;
        public final String message;

        private ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String m) {
            return new ValidationResult(false, m);
        }
    }
}