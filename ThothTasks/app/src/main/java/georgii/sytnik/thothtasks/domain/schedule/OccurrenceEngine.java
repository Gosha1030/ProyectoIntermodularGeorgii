package georgii.sytnik.thothtasks.domain.schedule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public final class OccurrenceEngine {

    private OccurrenceEngine() {}

    public static boolean isActiveOnDay(TaskEntity t, long startUtcMs, Calendar day) {
        if (t == null) return false;

        // Empty sin restricciones => transparente (no “ocupa”, pero permite)
        if (isEmptyWithoutRestrictions(t)) return true;

        if (!typeAllows(t, startUtcMs, day)) return false;
        if (!daysOfAllows(t, day)) return false;
        if (!periodicAllows(t, startUtcMs, day)) return false;

        return true;
    }

    private static boolean typeAllows(TaskEntity t, long startUtcMs, Calendar day) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(startUtcMs);
        zeroTime(start);

        Calendar d = (Calendar) day.clone();
        zeroTime(d);

        long diffDays = (d.getTimeInMillis() - start.getTimeInMillis()) / (24L * 60 * 60 * 1000);
        if (diffDays < 0) return false;

        switch (t.type) {
            case "Unique":
                return t.periodD != null && diffDays < t.periodD;
            default:
                return true;
        }
    }

    private static boolean daysOfAllows(TaskEntity t, Calendar day) {
        if (t.daysOfJson == null || t.daysOfJson.trim().isEmpty()) return true;

        try {
            JSONObject obj = new JSONObject(t.daysOfJson);
            String kind = obj.optString("kind", "");
            JSONArray arr = obj.optJSONArray("values");
            if (arr == null) return true;

            if ("weekdays".equals(kind)) {
                Set<Integer> allowed = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) allowed.add(arr.getInt(i)); // 1=Mon..7=Sun

                int cal = day.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
                int our = (cal == Calendar.SUNDAY) ? 7 : (cal - 1);
                return allowed.contains(our);
            }

            if ("monthdays".equals(kind)) {
                int m = day.get(Calendar.MONTH) + 1;
                int d = day.get(Calendar.DAY_OF_MONTH);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject md = arr.getJSONObject(i);
                    if (md.getInt("m") == m && md.getInt("d") == d) return true;
                }
                return false;
            }

        } catch (Exception ignored) {}

        return true;
    }

    private static boolean periodicAllows(TaskEntity t, long startUtcMs, Calendar day) {
        if (t.periodicJson == null || t.periodicJson.trim().isEmpty()) return true;

        try {
            JSONObject o = new JSONObject(t.periodicJson);
            String unit = o.optString("unit", "day");
            int amount = Math.max(1, o.optInt("amount", 1));
            int streak = Math.max(1, o.optInt("streakDays", 1));

            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(startUtcMs);
            zeroTime(start);

            Calendar d = (Calendar) day.clone();
            zeroTime(d);

            long diffDays = (d.getTimeInMillis() - start.getTimeInMillis()) / (24L * 60 * 60 * 1000);
            if (diffDays < 0) return false;

            if ("day".equals(unit)) {
                long mod = diffDays % amount;
                return mod < streak;
            }

            if ("week".equals(unit)) {
                long weeks = diffDays / 7;
                return (weeks % amount) == 0;
            }

            if ("month".equals(unit)) {
                int months = (d.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12
                        + (d.get(Calendar.MONTH) - start.get(Calendar.MONTH));
                return months >= 0 && (months % amount) == 0;
            }

            if ("year".equals(unit)) {
                int years = d.get(Calendar.YEAR) - start.get(Calendar.YEAR);
                return years >= 0 && (years % amount) == 0;
            }

        } catch (Exception ignored) {}

        return true;
    }

    private static boolean isEmptyWithoutRestrictions(TaskEntity t) {
        return "Empty".equals(t.type)
                && t.startTimeMin == null
                && t.finishTimeMin == null
                && t.timeM == null
                && (t.daysOfJson == null || t.daysOfJson.trim().isEmpty())
                && (t.periodicJson == null || t.periodicJson.trim().isEmpty())
                && t.periodD == null;
    }

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}