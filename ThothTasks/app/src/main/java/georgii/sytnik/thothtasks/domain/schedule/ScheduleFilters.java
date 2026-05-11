package georgii.sytnik.thothtasks.domain.schedule;

import org.json.JSONObject;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public final class ScheduleFilters {

    private ScheduleFilters() {}

    public static boolean isEmptyWithoutRestrictions(TaskEntity t) {
        return "Empty".equals(t.type)
                && t.startTimeMin == null
                && t.finishTimeMin == null
                && t.timeM == null
                && (t.daysOfJson == null || t.daysOfJson.trim().isEmpty())
                && (t.periodicJson == null || t.periodicJson.trim().isEmpty())
                && t.periodD == null;
    }

    /** periodic repeat interval (days approximation) used ONLY for filtering in month/year */
    public static Integer periodicIntervalDays(TaskEntity t) {
        if (!"Periodic".equals(t.type)) return null;
        if (t.periodicJson == null || t.periodicJson.trim().isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(t.periodicJson);
            String unit = o.optString("unit", "day");
            int amount = Math.max(1, o.optInt("amount", 1));
            switch (unit) {
                case "day": return amount;
                case "week": return amount * 7;
                case "month": return amount * 30;
                case "year": return amount * 365;
                default: return amount;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Yearly task that repeats each month (monthly-like) => exclude in YEAR view */
    public static boolean isMonthlyLikeYearly(TaskEntity t) {
        if (!"Yearly".equals(t.type)) return false;
        if (t.daysOfJson == null || t.daysOfJson.trim().isEmpty()) return false;

        try {
            JSONObject obj = new JSONObject(t.daysOfJson);
            if (!"monthdays".equals(obj.optString("kind", ""))) return false;

            boolean[] hasMonth = new boolean[13];
            var arr = obj.optJSONArray("values");
            if (arr == null) return false;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject md = arr.getJSONObject(i);
                int m = md.optInt("m", 0);
                if (m >= 1 && m <= 12) hasMonth[m] = true;
            }

            for (int m = 1; m <= 12; m++) if (!hasMonth[m]) return false;
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    // --- per view rules ---

    /** Week: do NOT show Daily. */
    public static boolean showInWeek(TaskEntity t) {
        if (isEmptyWithoutRestrictions(t)) return false;
        return !"Daily".equals(t.type);
    }

    /** Month: do NOT show Daily, Weekly. If Periodic <= 7 days => hide. */
    public static boolean showInMonth(TaskEntity t) {
        if (isEmptyWithoutRestrictions(t)) return false;
        if ("Daily".equals(t.type)) return false;
        if ("Weekly".equals(t.type)) return false;

        if ("Periodic".equals(t.type)) {
            Integer d = periodicIntervalDays(t);
            if (d != null && d <= 7) return false;
        }
        return true;
    }

    /** Year: do NOT show Daily, Weekly, monthly-like Yearly; Periodic <= 28 days => hide. */
    public static boolean showInYear(TaskEntity t) {
        if (isEmptyWithoutRestrictions(t)) return false;
        if ("Daily".equals(t.type)) return false;
        if ("Weekly".equals(t.type)) return false;

        if (isMonthlyLikeYearly(t)) return false;

        if ("Periodic".equals(t.type)) {
            Integer d = periodicIntervalDays(t);
            if (d != null && d <= 28) return false;
        }
        return true;
    }
}
