package georgii.sytnik.thothtasks.util;

import android.content.Context;
import android.content.res.Resources;

import georgii.sytnik.thothtasks.R;

public final class TaskTypeUi {

    private TaskTypeUi() {
    }

    /**
     * Returns localized label for a stable DB value: Unique/Daily/Weekly/Yearly/Periodic/Empty.
     */
    public static String label(Context ctx, String typeValue) {
        if (ctx == null || typeValue == null) return "";

        Resources r = ctx.getResources();
        String[] values = r.getStringArray(R.array.task_type_values);
        String[] labels = r.getStringArray(R.array.task_type_labels);

        int idx = indexOf(values, typeValue);
        if (idx >= 0 && idx < labels.length) return labels[idx];
        return typeValue;
    }

    /**
     * Returns stable DB value from spinner position (must use task_type_values).
     */
    public static String valueAt(Context ctx, int spinnerPosition) {
        String[] values = ctx.getResources().getStringArray(R.array.task_type_values);
        if (spinnerPosition < 0 || spinnerPosition >= values.length) return values[0];
        return values[spinnerPosition];
    }

    /**
     * Returns spinner index for stable DB value.
     */
    public static int indexOfValue(Context ctx, String typeValue) {
        String[] values = ctx.getResources().getStringArray(R.array.task_type_values);
        int idx = indexOf(values, typeValue);
        return (idx >= 0) ? idx : 0;
    }

    private static int indexOf(String[] arr, String v) {
        if (arr == null || v == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (v.equals(arr[i])) return i;
        }
        return -1;
    }
}