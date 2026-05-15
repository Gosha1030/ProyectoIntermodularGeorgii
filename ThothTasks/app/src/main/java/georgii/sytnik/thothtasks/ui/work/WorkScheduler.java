package georgii.sytnik.thothtasks.ui.work;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class WorkScheduler {

    private static final String WORK_NAME = "update_check";

    private WorkScheduler() {
    }

    public static void ensureUpdateCheckScheduled(Context ctx) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();

        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(UpdateCheckWorker.class, 15, TimeUnit.MINUTES).setConstraints(constraints).build();

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }
}