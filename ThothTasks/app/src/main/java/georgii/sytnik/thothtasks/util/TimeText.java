package georgii.sytnik.thothtasks.util;

import java.util.Calendar;

/**
 * Time helpers used across UI and scheduling.
 */
public final class TimeText {
    private TimeText() {
    }

    public static String minutesToText(Integer min) {
        if (min == null) return "";
        int h = min / 60;
        int m = min % 60;
        return String.format("%02d:%02d", h, m);
    }

    public static Integer parseTimeToMinutes(String hhmm) {
        if (hhmm == null || hhmm.isEmpty()) return null;
        try {
            String[] p = hhmm.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return h * 60 + m;
        } catch (Exception e) {
            return null;
        }
    }

    public static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}