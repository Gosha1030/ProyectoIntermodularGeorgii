package georgii.sytnik.thothtasks.ui;

import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.action.ActionPlanner;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.security.SettingsJson;

public class SettingsActivity extends AppCompatActivity {

    private AppDatabase db;

    private TextView tvDndStatus, tvNotifStatus;
    private MaterialButton btnGrantDnd, btnGrantNotif, btnSave;
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

        tvDndStatus = findViewById(R.id.tvDndStatus);
        tvNotifStatus = findViewById(R.id.tvNotifStatus);
        btnGrantDnd = findViewById(R.id.btnGrantDnd);
        btnGrantNotif = findViewById(R.id.btnGrantNotif);
        btnSave = findViewById(R.id.btnSave);

        swAlarmSound = findViewById(R.id.swAlarmSound);
        swAlarmVibrate = findViewById(R.id.swAlarmVibrate);
        swAskPassword = findViewById(R.id.swAskPassword);

        etPlanDays = findViewById(R.id.etPlanDays);
        etTravelMandatory = findViewById(R.id.etTravelMandatory);
        etTravelOptional = findViewById(R.id.etTravelOptional);

        btnGrantDnd.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } else {
                Toast.makeText(this, "DND no disponible", Toast.LENGTH_SHORT).show();
            }
        });

        btnGrantNotif.setOnClickListener(v -> requestNotifPermissionIfNeeded());

        btnSave.setOnClickListener(v -> saveSettings());

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionLabels();
    }

    private void load() {
        new Thread(() -> {
            userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            user = db.userDao().findById(userId);
            if (user == null) return;

            settings = SettingsJson.parseOrEmpty(user.ajustesJson);

            // Defaults
            boolean alarmSound = SettingsJson.getBool(settings, "alarmEnabledSound", true);
            boolean alarmVib = SettingsJson.getBool(settings, "alarmVibrate", true);
            int planDays = SettingsJson.getInt(settings, "actionPlanDaysAhead", 60);

            int mand = SettingsJson.getInt(settings, "travelExtraMandatoryM", 0);
            int opt = SettingsJson.getInt(settings, "travelExtraOptionalM", 0);

            boolean askPass = SettingsJson.getBool(settings, "askPassword", true);

            runOnUiThread(() -> {
                swAlarmSound.setChecked(alarmSound);
                swAlarmVibrate.setChecked(alarmVib);
                etPlanDays.setText(String.valueOf(planDays));

                etTravelMandatory.setText(String.valueOf(mand));
                etTravelOptional.setText(String.valueOf(opt));

                swAskPassword.setChecked(askPass);

                updatePermissionLabels();
            });
        }).start();
    }

    private void updatePermissionLabels() {
        // DND
        boolean dndGranted = false;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && nm != null) {
            dndGranted = nm.isNotificationPolicyAccessGranted();
        }
        tvDndStatus.setText(dndGranted ? "DND access: GRANTED" : "DND access: NOT GRANTED");

        // Notifications permission (Android 13+)
        boolean notifGranted = true;
        if (Build.VERSION.SDK_INT >= 33) {
            notifGranted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        tvNotifStatus.setText(notifGranted ? "Notificaciones: GRANTED" : "Notificaciones: NOT GRANTED");
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9001);
            } else {
                Toast.makeText(this, "Ya concedido", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No requerido en esta versión", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettings() {
        if (user == null || settings == null) return;

        boolean alarmSound = swAlarmSound.isChecked();
        boolean alarmVib = swAlarmVibrate.isChecked();
        boolean askPass = swAskPassword.isChecked();

        int planDays = parseIntOrDefault(etPlanDays, 60);
        int mand = parseIntOrDefault(etTravelMandatory, 0);
        int opt = parseIntOrDefault(etTravelOptional, 0);

        SettingsJson.putBool(settings, "alarmEnabledSound", alarmSound);
        SettingsJson.putBool(settings, "alarmVibrate", alarmVib);
        SettingsJson.putInt(settings, "actionPlanDaysAhead", clamp(planDays, 1, 365));
        SettingsJson.putInt(settings, "travelExtraMandatoryM", Math.max(0, mand));
        SettingsJson.putInt(settings, "travelExtraOptionalM", Math.max(0, opt));
        SettingsJson.putBool(settings, "askPassword", askPass);

        user.ajustesJson = settings.toString();

        new Thread(() -> {
            db.userDao().update(user);

            // Replan actions with new horizon
            int horizon = SettingsJson.getInt(settings, "actionPlanDaysAhead", 60);
            ActionPlanner.scheduleNextDays(getApplicationContext(), db, horizon);

            runOnUiThread(() -> Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private int parseIntOrDefault(TextInputEditText et, int def) {
        try {
            String s = et.getText() != null ? et.getText().toString().trim() : "";
            if (s.isEmpty()) return def;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}