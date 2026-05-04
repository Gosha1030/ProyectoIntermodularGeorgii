package georgii.sytnik.thothtasks.domain.schedule;

import org.json.JSONObject;

import java.util.Calendar;

import georgii.sytnik.thothtasks.db.entities.TaskEntity;

public final class ScheduleHorizon {

    private ScheduleHorizon() {}

    public static long computeEndUtc(TaskEntity parent, long parentStartUtc, Long parentDeactivateUtc) {
        // Empty: solo si tiene restricciones; si no, no se genera subhorario (pero esto se filtra antes)
        if ("Empty".equals(parent.type)) {
            if (parentDeactivateUtc != null) return parentDeactivateUtc;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(parentStartUtc);
            c.add(Calendar.YEAR, 2);
            return c.getTimeInMillis();
        }

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(parentStartUtc);

        switch (parent.type) {
            case "Daily":
                c.add(Calendar.DATE, 1);
                return c.getTimeInMillis();

            case "Weekly":
                c.add(Calendar.DATE, 7);
                return c.getTimeInMillis();

            case "Yearly":
                c.add(Calendar.YEAR, 1);
                return c.getTimeInMillis();

            case "Unique":
                c.add(Calendar.DATE, parent.periodD != null ? parent.periodD : 1);
                return c.getTimeInMillis();

            case "Periodic":
                return addThreePeriods(parent, parentStartUtc);

            default:
                // fallback seguro
                c.add(Calendar.YEAR, 1);
                return c.getTimeInMillis();
        }
    }

    private static long addThreePeriods(TaskEntity parent, long startUtc) {
        // “tres primeros periodos” depende de unit/amount del Periodic JSON
        String unit = "day";
        int amount = 1;

        try {
            if (parent.periodicJson != null && !parent.periodicJson.trim().isEmpty()) {
                JSONObject o = new JSONObject(parent.periodicJson);
                unit = o.optString("unit", "day");
                amount = Math.max(1, o.optInt("amount", 1));
            }
        } catch (Exception ignored) {}

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startUtc);

        int periods = 3 * amount;

        switch (unit) {
            case "day":
                c.add(Calendar.DATE, periods);
                break;
            case "week":
                c.add(Calendar.DATE, periods * 7);
                break;
            case "month":
                c.add(Calendar.MONTH, periods);
                break;
            case "year":
                c.add(Calendar.YEAR, periods);
                break;
            default:
                c.add(Calendar.DATE, periods);
        }

        return c.getTimeInMillis();
    }
}