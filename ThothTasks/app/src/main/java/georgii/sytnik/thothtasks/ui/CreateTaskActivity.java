package georgii.sytnik.thothtasks.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.action.ActionChangeTypes;
import georgii.sytnik.thothtasks.domain.action.ActionKeys;
import georgii.sytnik.thothtasks.domain.action.ActionPlanner;
import georgii.sytnik.thothtasks.domain.validation.TaskHierarchyValidator;
import georgii.sytnik.thothtasks.security.ActionPlanHorizon;
import georgii.sytnik.thothtasks.security.ActionSettingsReader;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;

public class CreateTaskActivity extends AppCompatActivity {

    public static final String EXTRA_FATHER_ID = "father_id";
    public static final int REQ_PICK_FATHER = 1001;

    private AppDatabase db;

    private TextInputEditText etName, etStart, etFinish, etTimeM, etWeight, etPeriodD;
    private CheckBox cbUninterrupted;
    private Spinner spType;
    private TextInputLayout tilPeriodD;
    private MaterialButton btnPickFather, btnCreate;
    private TextView tvFather;

    private byte[] selectedFatherId; // nullable
    private String selectedFatherName;

    private MaterialButton btnWhenApply;
    private TextView tvWhenApply;
    private Long whenApplyUtcMs = null; // null => aplica ahora
    private FrameLayout typeInfoContainer;
    private View typeInfoView;

    // Weekly
    private CheckBox cbMon, cbTue, cbWed, cbThu, cbFri, cbSat, cbSun;

    // Periodic
    private Spinner spUnit;
    private EditText etAmount, etStreak;

    // Yearly
    private EditText etMonthDay;
    private MaterialButton btnAdd;
    private TextView tvList;
    private final List<int[]> yearlyPairs = new ArrayList<>(); // {m,d}

    // Place
    private byte[] selectedPlaceId = null;
    private String selectedPlaceName = "(cualquier lugar)";
    private TextView tvPlace;

    // ✅ Actions switches
    private SwitchCompat swAlarm, swDnd, swNotifyMonth, swNotifyWeek, swNotifyDay, swNotifyOnDay, swNotify1h, swNotify10m, swNotify1m;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        db = AppDatabase.get(this);

        etName = findViewById(R.id.etName);
        etStart = findViewById(R.id.etStart);
        etFinish = findViewById(R.id.etFinish);
        etTimeM = findViewById(R.id.etTimeM);
        etWeight = findViewById(R.id.etWeight);
        etPeriodD = findViewById(R.id.etPeriodD);

        cbUninterrupted = findViewById(R.id.cbUninterrupted);
        spType = findViewById(R.id.spType);
        typeInfoContainer = findViewById(R.id.typeInfoContainer);

        btnPickFather = findViewById(R.id.btnPickFather);
        btnCreate = findViewById(R.id.btnCreate);
        tvFather = findViewById(R.id.tvFather);

        btnWhenApply = findViewById(R.id.btnWhenApply);
        tvWhenApply = findViewById(R.id.tvWhenApply);

        // Place UI
        tvPlace = findViewById(R.id.tvPlace);
        MaterialButton btnPickPlace = findViewById(R.id.btnPickPlace);
        btnPickPlace.setOnClickListener(v -> {
            Intent i = new Intent(this, PlacePickerActivity.class);
            i.putExtra(PlacePickerActivity.EXTRA_ALLOW_ANY, true);
            pickPlaceLauncher.launch(i);
        });

        // ✅ Action switches (must exist in XML)
        swAlarm = findViewById(R.id.swAlarm);
        swDnd = findViewById(R.id.swDnd);
        swNotifyMonth = findViewById(R.id.swNotifyMonth);
        swNotifyWeek = findViewById(R.id.swNotifyWeek);
        swNotifyDay = findViewById(R.id.swNotifyDay);
        swNotifyOnDay = findViewById(R.id.swNotifyOnDay);
        swNotify1h = findViewById(R.id.swNotify1h);
        swNotify10m = findViewById(R.id.swNotify10m);
        swNotify1m = findViewById(R.id.swNotify1m);

        btnWhenApply.setOnClickListener(v -> pickWhenApply());

        // Types
        String[] types = new String[] {"Unique", "Daily", "Weekly", "Yearly", "Periodic", "Empty"};
        spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));

        // Father picker
        btnPickFather.setOnClickListener(v -> startActivityForResult(new Intent(this, TaskPickerActivity.class), REQ_PICK_FATHER));

        // Time picker
        etStart.setOnClickListener(v -> showTimePicker(etStart));
        etFinish.setOnClickListener(v -> showTimePicker(etFinish));

        tilPeriodD = findViewById(R.id.tilPeriodD);

        // Type info + PeriodD
        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = (String) parent.getItemAtPosition(position);
                updateTypeInfoUI(type);

                if ("Daily".equals(type)) {
                    tilPeriodD.setVisibility(View.GONE);
                    etPeriodD.setText(null);
                } else {
                    tilPeriodD.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // init
        updateTypeInfoUI((String) spType.getSelectedItem());

        btnCreate.setOnClickListener(v -> createTask());
    }

    private void showTimePicker(TextInputEditText target) {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String s = String.format("%02d:%02d", hourOfDay, minute);
            target.setText(s);
        }, h, m, true).show();
    }

    private void createTask() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) name = "NewTask";

        String type = (String) spType.getSelectedItem();

        Integer startMin = parseTimeToMinutes(etStart.getText() != null ? etStart.getText().toString().trim() : "");
        Integer finishMin = parseTimeToMinutes(etFinish.getText() != null ? etFinish.getText().toString().trim() : "");

        // no crossing midnight
        if (startMin != null && finishMin != null && startMin >= finishMin) {
            Toast.makeText(this, R.string.toast_invalid_time_order, Toast.LENGTH_SHORT).show();
            return;
        }

        // exact time exists only if both exist (your rule)
        final boolean hasExactTime = (startMin != null && finishMin != null);

        Integer timeM = parseInt(etTimeM.getText() != null ? etTimeM.getText().toString().trim() : "");
        Integer weight = parseInt(etWeight.getText() != null ? etWeight.getText().toString().trim() : "");
        Integer periodD = parseInt(etPeriodD.getText() != null ? etPeriodD.getText().toString().trim() : "");

        boolean uninterrupted = cbUninterrupted.isChecked();

        // build type-specific JSON
        String daysOfJson = null;
        String periodicJson = null;

        try {
            if ("Weekly".equals(type)) {
                JSONObject obj = new JSONObject();
                obj.put("kind", "weekdays");
                JSONArray arr = new JSONArray();
                if (cbMon != null && cbMon.isChecked()) arr.put(1);
                if (cbTue != null && cbTue.isChecked()) arr.put(2);
                if (cbWed != null && cbWed.isChecked()) arr.put(3);
                if (cbThu != null && cbThu.isChecked()) arr.put(4);
                if (cbFri != null && cbFri.isChecked()) arr.put(5);
                if (cbSat != null && cbSat.isChecked()) arr.put(6);
                if (cbSun != null && cbSun.isChecked()) arr.put(7);
                obj.put("values", arr);
                daysOfJson = obj.toString();
            } else if ("Periodic".equals(type)) {
                JSONObject obj = new JSONObject();
                obj.put("unit", spUnit != null ? (String) spUnit.getSelectedItem() : "day");
                obj.put("amount", safeInt(etAmount, 1));
                obj.put("streakDays", safeInt(etStreak, 1));
                periodicJson = obj.toString();
            } else if ("Yearly".equals(type)) {
                JSONObject obj = new JSONObject();
                obj.put("kind", "monthdays");
                JSONArray arr = new JSONArray();
                for (int[] md : yearlyPairs) {
                    JSONObject pair = new JSONObject();
                    pair.put("m", md[0]);
                    pair.put("d", md[1]);
                    arr.put(pair);
                }
                obj.put("values", arr);
                daysOfJson = obj.toString();
            }
        } catch (Exception ignored) {}

        // Snapshot actions (read on UI thread)
        final boolean aAlarm = swAlarm != null && swAlarm.isChecked();
        final boolean aDnd = swDnd != null && swDnd.isChecked();
        final boolean aMonth = swNotifyMonth != null && swNotifyMonth.isChecked();
        final boolean aWeek = swNotifyWeek != null && swNotifyWeek.isChecked();
        final boolean aDay = swNotifyDay != null && swNotifyDay.isChecked();
        final boolean aOnDay = swNotifyOnDay != null && swNotifyOnDay.isChecked();
        final boolean a1h = swNotify1h != null && swNotify1h.isChecked();
        final boolean a10m = swNotify10m != null && swNotify10m.isChecked();
        final boolean a1m = swNotify1m != null && swNotify1m.isChecked();

        String finalName = name;
        String finalDaysOfJson = daysOfJson;
        String finalPeriodicJson = periodicJson;

        new Thread(() -> {
            byte[] userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            UserEntity u = db.userDao().findById(userId);
            if (u == null) return;

            byte[] fatherId = selectedFatherId != null ? selectedFatherId : u.taskRoot;

            TaskEntity t = new TaskEntity();
            t.taskId = uuidToBytes(UuidV7.newUuid());
            t.taskFather = fatherId;
            t.taskName = finalName;
            t.type = type;
            t.startTimeMin = startMin;
            t.finishTimeMin = finishMin;
            t.timeM = timeM;
            t.weight = weight;
            t.periodD = periodD;
            t.uninterrupted = uninterrupted;
            t.daysOfJson = finalDaysOfJson;
            t.periodicJson = finalPeriodicJson;
            t.placeId = selectedPlaceId;
            t.state = true;
            t.muted = false;

            // ✅ Action JSON
            t.actionJson = buildActionJson(hasExactTime, aAlarm, aDnd, aMonth, aWeek, aDay, aOnDay, a1h, a10m, a1m);

            if (!validateCreateTask(t.type, t.periodicJson, t.periodD)) return;

            TaskHierarchyValidator.ValidationResult vr =
                    TaskHierarchyValidator.canChildExistInsideParent(
                            db,
                            t,
                            whenApplyUtcMs,
                            fatherId
                    );

            if (!vr.ok) {
                runOnUiThread(() -> Toast.makeText(this, vr.message, Toast.LENGTH_LONG).show());
                return;
            }

            db.taskDao().insert(t);

            long now = System.currentTimeMillis();

            // TaskChange: create_task
            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
            ch.taskId = t.taskId;
            ch.newTaskId = null;
            ch.type = "create_task";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = whenApplyUtcMs;
            db.taskChangeDao().insert(ch);

            // TaskChange(s) for enabled actions at creation (one per action)
            insertActionTaskChanges(t.taskId, t.actionJson, whenApplyUtcMs);

            int horizon = ActionPlanHorizon.getDaysAhead(this, db);
            ActionPlanner.scheduleNextDays(getApplicationContext(), db, horizon);

            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_task_created, Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    private String buildActionJson(boolean hasExactTime,
                                   boolean alarm, boolean dnd,
                                   boolean nMonth, boolean nWeek, boolean nDay, boolean nOnDay,
                                   boolean n1h, boolean n10m, boolean n1m) {
        try {
            JSONObject o = new JSONObject();

            // general actions
            o.put(ActionKeys.NOTIFY_MONTH, nMonth);
            o.put(ActionKeys.NOTIFY_WEEK, nWeek);
            o.put(ActionKeys.NOTIFY_DAY, nDay);
            o.put(ActionKeys.NOTIFY_ON_DAY, nOnDay);
            o.put(ActionKeys.DND, dnd);

            // timed actions only if start+finish exist
            o.put(ActionKeys.ALARM, hasExactTime && alarm);
            o.put(ActionKeys.NOTIFY_1H, hasExactTime && n1h);
            o.put(ActionKeys.NOTIFY_10M, hasExactTime && n10m);
            o.put(ActionKeys.NOTIFY_1M, hasExactTime && n1m);

            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void insertActionTaskChanges(byte[] taskId, String actionJson, Long whenApplyUtcMs) {
        try {
            JSONObject o = new JSONObject(actionJson);

            String[] keys = new String[] {
                    ActionKeys.ALARM,
                    ActionKeys.DND,
                    ActionKeys.NOTIFY_MONTH,
                    ActionKeys.NOTIFY_WEEK,
                    ActionKeys.NOTIFY_DAY,
                    ActionKeys.NOTIFY_ON_DAY,
                    ActionKeys.NOTIFY_1H,
                    ActionKeys.NOTIFY_10M,
                    ActionKeys.NOTIFY_1M
            };

            long now = System.currentTimeMillis();

            for (String k : keys) {
                if (!o.optBoolean(k, false)) continue;

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
                ch.taskId = taskId;
                ch.newTaskId = null;
                ch.type = ActionChangeTypes.on(k); // e.g. alarm_on
                ch.createAtUtcMs = now;
                ch.whenApplyUtcMs = whenApplyUtcMs; // align with create_task start date
                db.taskChangeDao().insert(ch);
            }
        } catch (Exception ignored) {}
    }

    private static Integer parseTimeToMinutes(String hhmm) {
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

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[] {
                (byte)(msb >>> 56), (byte)(msb >>> 48), (byte)(msb >>> 40), (byte)(msb >>> 32),
                (byte)(msb >>> 24), (byte)(msb >>> 16), (byte)(msb >>>  8), (byte)(msb),
                (byte)(lsb >>> 56), (byte)(lsb >>> 48), (byte)(lsb >>> 40), (byte)(lsb >>> 32),
                (byte)(lsb >>> 24), (byte)(lsb >>> 16), (byte)(lsb >>>  8), (byte)(lsb)
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FATHER && resultCode == RESULT_OK && data != null) {
            selectedFatherId = data.getByteArrayExtra(TaskPickerActivity.RESULT_TASK_ID);
            selectedFatherName = data.getStringExtra(TaskPickerActivity.RESULT_TASK_NAME);
            if (selectedFatherName != null) tvFather.setText(selectedFatherName);
        }
    }

    private void pickWhenApply() {
        Calendar c = Calendar.getInstance();

        new DatePickerDialog(this, (dp, y, m, d) -> {
            Calendar c2 = Calendar.getInstance();
            c2.set(Calendar.YEAR, y);
            c2.set(Calendar.MONTH, m);
            c2.set(Calendar.DAY_OF_MONTH, d);

            new TimePickerDialog(this, (tp, hh, mm) -> {
                c2.set(Calendar.HOUR_OF_DAY, hh);
                c2.set(Calendar.MINUTE, mm);
                c2.set(Calendar.SECOND, 0);
                c2.set(Calendar.MILLISECOND, 0);

                whenApplyUtcMs = c2.getTimeInMillis();
                tvWhenApply.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(c2.getTime()));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();

        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateTypeInfoUI(String type) {
        typeInfoContainer.removeAllViews();
        typeInfoView = null;

        if ("Weekly".equals(type)) {
            typeInfoView = getLayoutInflater().inflate(R.layout.view_typeinfo_weekly, typeInfoContainer, false);
            bindWeekly(typeInfoView);
            typeInfoContainer.addView(typeInfoView);
        } else if ("Periodic".equals(type)) {
            typeInfoView = getLayoutInflater().inflate(R.layout.view_typeinfo_periodic, typeInfoContainer, false);
            bindPeriodic(typeInfoView);
            typeInfoContainer.addView(typeInfoView);
        } else if ("Yearly".equals(type)) {
            typeInfoView = getLayoutInflater().inflate(R.layout.view_typeinfo_yearly, typeInfoContainer, false);
            bindYearly(typeInfoView);
            typeInfoContainer.addView(typeInfoView);
        }
    }

    private void bindWeekly(View v) {
        cbMon = v.findViewById(R.id.cbMon);
        cbTue = v.findViewById(R.id.cbTue);
        cbWed = v.findViewById(R.id.cbWed);
        cbThu = v.findViewById(R.id.cbThu);
        cbFri = v.findViewById(R.id.cbFri);
        cbSat = v.findViewById(R.id.cbSat);
        cbSun = v.findViewById(R.id.cbSun);
    }

    private void bindPeriodic(View v) {
        spUnit = v.findViewById(R.id.spUnit);
        etAmount = v.findViewById(R.id.etAmount);
        etStreak = v.findViewById(R.id.etStreak);

        String[] units = new String[] {"day","week","month","year"};
        spUnit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, units));
    }

    private void bindYearly(View v) {
        etMonthDay = v.findViewById(R.id.etMonthDay);
        btnAdd = v.findViewById(R.id.btnAdd);
        tvList = v.findViewById(R.id.tvList);

        yearlyPairs.clear();
        renderYearlyList();

        btnAdd.setOnClickListener(x -> {
            String s = etMonthDay.getText() != null ? etMonthDay.getText().toString().trim() : "";
            int[] md = parseMonthDay(s);
            if (md == null) return;
            yearlyPairs.add(md);
            etMonthDay.setText("");
            renderYearlyList();
        });
    }

    private void renderYearlyList() {
        if (tvList == null) return;
        if (yearlyPairs.isEmpty()) {
            tvList.setText("(empty)");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int[] md : yearlyPairs) {
            sb.append(String.format("%02d-%02d", md[0], md[1])).append("  ");
        }
        tvList.setText(sb.toString().trim());
    }

    private int[] parseMonthDay(String s) {
        try {
            String[] p = s.split("-");
            if (p.length != 2) return null;
            int m = Integer.parseInt(p[0]);
            int d = Integer.parseInt(p[1]);
            if (m < 1 || m > 12) return null;
            if (d < 1 || d > 31) return null;
            return new int[]{m,d};
        } catch (Exception e) {
            return null;
        }
    }

    private static int safeInt(EditText et, int def) {
        try {
            String s = et.getText() == null ? "" : et.getText().toString().trim();
            if (s.isEmpty()) return def;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private boolean validateCreateTask(String type, String periodicJson, Integer periodD) {
        if ("Daily".equals(type) && periodD != null) {
            Toast.makeText(this, R.string.err_periodd_not_for_daily, Toast.LENGTH_LONG).show();
            return false;
        }

        if ("Periodic".equals(type) && periodicJson != null && !periodicJson.isEmpty()) {
            try {
                JSONObject o = new JSONObject(periodicJson);
                String unit = o.optString("unit", "");
                int amount = o.optInt("amount", 1);

                if (amount == 1) {
                    Toast.makeText(this, R.string.err_periodic_amount_1, Toast.LENGTH_LONG).show();
                    return false;
                }
                if ("day".equals(unit) && amount == 7) {
                    Toast.makeText(this, R.string.err_periodic_day_7, Toast.LENGTH_LONG).show();
                    return false;
                }
                if ("month".equals(unit) && amount == 12) {
                    Toast.makeText(this, R.string.err_periodic_month_12, Toast.LENGTH_LONG).show();
                    return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> pickPlaceLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                    selectedPlaceId = res.getData().getByteArrayExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_ID);
                    selectedPlaceName = res.getData().getStringExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_NAME);
                    if (selectedPlaceName == null) selectedPlaceName = "(cualquier lugar)";
                    tvPlace.setText(selectedPlaceName);
                }
            });
}
