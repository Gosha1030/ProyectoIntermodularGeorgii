package georgii.sytnik.thothtasks.ui;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.action.ActionPlanner;
import georgii.sytnik.thothtasks.security.ActionSettingsKeys;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.security.SettingsJson;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 3001;

    private AppDatabase db;

    private TextView tvDndStatus, tvNotifStatus;
    private MaterialButton btnGrantDnd, btnGrantNotif, btnEn, btnEs, btnSave;
    private SwitchCompat swAlarmSound, swAlarmVibrate, swAskPassword;
    private TextInputEditText etPlanDays, etTravelMandatory, etTravelOptional;

    private byte[] userId;
    private UserEntity user;
    private JSONObject settings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        db = AppDatabase.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.settings_title);

        tvDndStatus = findViewById(R.id.tvDndStatus);
        tvNotifStatus = findViewById(R.id.tvNotifStatus);
        btnGrantDnd = findViewById(R.id.btnGrantDnd);
        btnGrantNotif = findViewById(R.id.btnGrantNotif);
        btnEn = findViewById(R.id.btnEng);
        btnEs = findViewById(R.id.btnEsp);
        btnSave = findViewById(R.id.btnSave);

        swAlarmSound = findViewById(R.id.swAlarmSound);
        swAlarmVibrate = findViewById(R.id.swAlarmVibrate);
        swAskPassword = findViewById(R.id.swAskPassword);

        etPlanDays = findViewById(R.id.etPlanDays);
        etTravelMandatory = findViewById(R.id.etTravelMandatory);
        etTravelOptional = findViewById(R.id.etTravelOptional);

        btnGrantDnd.setOnClickListener(v -> grantDndAccess());
        btnGrantNotif.setOnClickListener(v -> requestNotifPermissionIfNeeded());
        btnSave.setOnClickListener(v -> save());

        btnEs.setOnClickListener(v -> setLocale("es"));
        btnEn.setOnClickListener(v -> setLocale("en"));


        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    private void load() {
        new Thread(() -> {
            userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;

            user = db.userDao().findById(userId);
            if (user == null) return;

            settings = SettingsJson.parseOrEmpty(user.ajustesJson);

            runOnUiThread(() -> {
                // Defaults
                boolean alarmSound = SettingsJson.getBool(settings, ActionSettingsKeys.ALARM_ENABLED_SOUND, true);
                boolean alarmVibrate = SettingsJson.getBool(settings, ActionSettingsKeys.ALARM_VIBRATE, true);

                int daysAhead = SettingsJson.getInt(settings, ActionSettingsKeys.ACTION_PLAN_DAYS_AHEAD, 60);
                int travelMandatory = SettingsJson.getInt(settings, ActionSettingsKeys.TRAVEL_EXTRA_MANDATORY_M, 0);
                int travelOptional = SettingsJson.getInt(settings, ActionSettingsKeys.TRAVEL_EXTRA_OPTIONAL_M, 0);

                boolean askPassword = SettingsJson.getBool(settings, ActionSettingsKeys.ASK_PASSWORD, false);

                swAlarmSound.setChecked(alarmSound);
                swAlarmVibrate.setChecked(alarmVibrate);
                swAskPassword.setChecked(askPassword);

                etPlanDays.setText(String.valueOf(daysAhead));
                etTravelMandatory.setText(String.valueOf(travelMandatory));
                etTravelOptional.setText(String.valueOf(travelOptional));

                refreshPermissionStatus();
            });
        }).start();
    }

    private void refreshPermissionStatus() {
        tvDndStatus.setText(isDndGranted() ? R.string.dnd_granted : R.string.dnd_not_granted);
        tvNotifStatus.setText(isNotifGranted() ? R.string.notif_granted : R.string.notif_not_granted);
    }

    private boolean isDndGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    private boolean isNotifGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true; // no runtime permission before 33
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void grantDndAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, R.string.toast_dnd_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isDndGranted()) {
            Toast.makeText(this, R.string.toast_already_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.toast_not_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isNotifGranted()) {
            Toast.makeText(this, R.string.toast_already_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_NOTIF);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            refreshPermissionStatus();
        }
    }

    private void save() {
        if (user == null || settings == null) return;

        boolean alarmSound = swAlarmSound.isChecked();
        boolean alarmVibrate = swAlarmVibrate.isChecked();
        boolean askPassword = swAskPassword.isChecked();

        Integer daysAhead = parseIntOrNull(textOf(etPlanDays));
        Integer travelMandatory = parseIntOrNull(textOf(etTravelMandatory));
        Integer travelOptional = parseIntOrNull(textOf(etTravelOptional));

        // Defaults if empty
        int finalDaysAhead = (daysAhead != null && daysAhead > 0) ? daysAhead : 60;
        int finalTravelMandatory = (travelMandatory != null && travelMandatory >= 0) ? travelMandatory : 0;
        int finalTravelOptional = (travelOptional != null && travelOptional >= 0) ? travelOptional : 0;

        SettingsJson.putBool(settings, ActionSettingsKeys.ALARM_ENABLED_SOUND, alarmSound);
        SettingsJson.putBool(settings, ActionSettingsKeys.ALARM_VIBRATE, alarmVibrate);
        SettingsJson.putInt(settings, ActionSettingsKeys.ACTION_PLAN_DAYS_AHEAD, finalDaysAhead);

        SettingsJson.putInt(settings, ActionSettingsKeys.TRAVEL_EXTRA_MANDATORY_M, finalTravelMandatory);
        SettingsJson.putInt(settings, ActionSettingsKeys.TRAVEL_EXTRA_OPTIONAL_M, finalTravelOptional);

        SettingsJson.putBool(settings, ActionSettingsKeys.ASK_PASSWORD, askPassword);

        new Thread(() -> {
            user.ajustesJson = settings.toString();
            db.userDao().update(user); // same persistence method you used before [1](blob:https://www.microsoft365.com/f4f275ef-97e2-4835-a3b5-82f9562625f7)

            // Replan with new horizon immediately
            ActionPlanner.scheduleNextDays(getApplicationContext(), db, finalDaysAhead);

            runOnUiThread(() -> Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show());
        }).start();
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private void setLocale(String lang) {
        String current = AppCompatDelegate.getApplicationLocales().toLanguageTags();

        if (current.equals(lang)) return; // evita reload innecesario

        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(lang)
        );

    }
}
