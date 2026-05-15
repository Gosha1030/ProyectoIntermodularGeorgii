package georgii.sytnik.thothtasks.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.TaskTypeUi;
import georgii.sytnik.thothtasks.util.TimeText;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class CreateTaskActivity extends AppCompatActivity {

    public static final int REQ_PICK_FATHER = 1001;
    private final List<int[]> yearlyPairs = new ArrayList<>();
    private AppDatabase db;
    private TextInputEditText etName, etStart, etFinish, etTimeM, etWeight, etPeriodD;
    private CheckBox cbUninterrupted;
    private Spinner spType;
    private TextInputLayout tilPeriodD;
    private MaterialButton btnPickFather, btnCreate, btnWhenApply;
    private TextView tvFather, tvWhenApply;
    private FrameLayout typeInfoContainer;
    private View typeInfoView;
    private byte[] selectedFatherId;
    private Long whenApplyUtcMs = null;
    private CheckBox cbMon, cbTue, cbWed, cbThu, cbFri, cbSat, cbSun;
    private Spinner spUnit;
    private EditText etAmount, etStreak;
    private EditText etMonthDay;
    private MaterialButton btnAdd;
    private TextView tvList;
    private byte[] selectedPlaceId = null;
    private TextView tvPlace;
    private final androidx.activity.result.ActivityResultLauncher<Intent> pickPlaceLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), res -> {
        if (res.getResultCode() == RESULT_OK && res.getData() != null) {
            selectedPlaceId = res.getData().getByteArrayExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_ID);
            String placeName = res.getData().getStringExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_NAME);
            if (placeName == null) placeName = getString(R.string.place_anywhere);
            tvPlace.setText(placeName);
        }
    });
    private SwitchCompat swAlarm, swDnd, swNotifyMonth, swNotifyWeek, swNotifyDay, swNotifyOnDay, swNotify1h, swNotify10m, swNotify1m;

    private static int safeInt(EditText et, int def) {
        try {
            String s = et.getText() == null ? "" : et.getText().toString().trim();
            if (s.isEmpty()) return def;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

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

        tilPeriodD = findViewById(R.id.tilPeriodD);

        tvPlace = findViewById(R.id.tvPlace);
        MaterialButton btnPickPlace = findViewById(R.id.btnPickPlace);
        btnPickPlace.setOnClickListener(v -> {
            Intent i = new Intent(this, PlacePickerActivity.class);
            i.putExtra(PlacePickerActivity.EXTRA_ALLOW_ANY, true);
            pickPlaceLauncher.launch(i);
        });

        swAlarm = findViewById(R.id.swAlarm);
        swDnd = findViewById(R.id.swDnd);
        swNotifyMonth = findViewById(R.id.swNotifyMonth);
        swNotifyWeek = findViewById(R.id.swNotifyWeek);
        swNotifyDay = findViewById(R.id.swNotifyDay);
        swNotifyOnDay = findViewById(R.id.swNotifyOnDay);
        swNotify1h = findViewById(R.id.swNotify1h);
        swNotify10m = findViewById(R.id.swNotify10m);
        swNotify1m = findViewById(R.id.swNotify1m);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(this, R.array.task_type_labels, android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);

        btnPickFather.setOnClickListener(v -> startActivityForResult(new Intent(this, TaskPickerActivity.class), REQ_PICK_FATHER));
        btnWhenApply.setOnClickListener(v -> pickWhenApply());

        etStart.setOnClickListener(v -> showTimePicker(etStart));
        etFinish.setOnClickListener(v -> showTimePicker(etFinish));

        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = (String) parent.getItemAtPosition(position);
                updateTypeInfoUI(type);

                if ("Daily".equals(type)) {
                    tilPeriodD.setVisibility(View.GONE);
                    etPeriodD.setText(null);
                } else {
                    tilPeriodD.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        updateTypeInfoUI((String) spType.getSelectedItem());

        TextWatcher availabilityWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateTimedActionsAvailability();
            }
        };
        etStart.addTextChangedListener(availabilityWatcher);
        etFinish.addTextChangedListener(availabilityWatcher);
        etTimeM.addTextChangedListener(availabilityWatcher);
        updateTimedActionsAvailability();

        btnCreate.setOnClickListener(v -> createTask());
    }

    private void updateTimedActionsAvailability() {
        Integer startMin = TimeText.parseTimeToMinutes(textOf(etStart));
        Integer finishMin = TimeText.parseTimeToMinutes(textOf(etFinish));
        Integer timeM = parseIntOrNull(textOf(etTimeM));

        boolean canTimedActions = (timeM != null) || (startMin != null && finishMin != null);

        swAlarm.setEnabled(canTimedActions);
        swDnd.setEnabled(canTimedActions);
        swNotify1h.setEnabled(canTimedActions);
        swNotify10m.setEnabled(canTimedActions);
        swNotify1m.setEnabled(canTimedActions);
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
        String name = textOf(etName);
        if (name.isEmpty()) name = getString(R.string.default_new_task_name);

        String type = TaskTypeUi.valueAt(this, spType.getSelectedItemPosition());

        Integer startMin = TimeText.parseTimeToMinutes(textOf(etStart));
        Integer finishMin = TimeText.parseTimeToMinutes(textOf(etFinish));

        if (startMin != null && finishMin != null && startMin >= finishMin) {
            Toast.makeText(this, R.string.toast_invalid_time_order, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer timeM = parseIntOrNull(textOf(etTimeM));
        Integer weight = parseIntOrNull(textOf(etWeight));
        Integer periodD = parseIntOrNull(textOf(etPeriodD));
        boolean uninterrupted = cbUninterrupted.isChecked();

        boolean canTimedActions = (timeM != null) || (startMin != null && finishMin != null);

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

                String unitValue = "day";
                if (spUnit != null) {
                    int pos = spUnit.getSelectedItemPosition();
                    String[] values = getResources().getStringArray(R.array.periodic_units_values);
                    if (pos >= 0 && pos < values.length) unitValue = values[pos];
                }

                obj.put("unit", unitValue);
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
        } catch (Exception e) {
        }

        boolean aAlarm = canTimedActions && swAlarm.isChecked();
        boolean aDnd = canTimedActions && swDnd.isChecked();
        boolean a1h = canTimedActions && swNotify1h.isChecked();
        boolean a10m = canTimedActions && swNotify10m.isChecked();
        boolean a1m = canTimedActions && swNotify1m.isChecked();

        boolean aMonth = swNotifyMonth.isChecked();
        boolean aWeek = swNotifyWeek.isChecked();
        boolean aDay = swNotifyDay.isChecked();
        boolean aOnDay = swNotifyOnDay.isChecked();

        String finalDaysOfJson = daysOfJson;
        String finalPeriodicJson = periodicJson;
        String finalName = name;

        new Thread(() -> {
            byte[] userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            UserEntity u = db.userDao().findById(userId);
            if (u == null) return;

            byte[] fatherId = (selectedFatherId != null) ? selectedFatherId : u.taskRoot;

            TaskEntity t = new TaskEntity();
            t.taskId = UuidBytes.uuidToBytes(UuidV7.newUuid());
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

            t.actionJson = buildActionJson(aAlarm, aDnd, aMonth, aWeek, aDay, aOnDay, a1h, a10m, a1m);

            if (!validateCreateTask(t.type, t.periodicJson, t.periodD)) return;

            TaskHierarchyValidator.ValidationResult vr = TaskHierarchyValidator.canChildExistInsideParent(db, t, whenApplyUtcMs, fatherId);

            if (!vr.ok) {
                runOnUiThread(() -> Toast.makeText(this, vr.message, Toast.LENGTH_LONG).show());
                return;
            }

            db.taskDao().insert(t);

            long now = System.currentTimeMillis();

            TaskChangeEntity create = new TaskChangeEntity();
            create.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            create.taskId = t.taskId;
            create.newTaskId = null;
            create.type = "create_task";
            create.createAtUtcMs = now;
            create.whenApplyUtcMs = whenApplyUtcMs;
            db.taskChangeDao().insert(create);

            insertActionTaskChanges(t.taskId, t.actionJson, whenApplyUtcMs);

            int horizon = ActionPlanHorizon.getDaysAhead(this, db);
            ActionPlanner.scheduleNextDays(getApplicationContext(), db, horizon);

            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_task_created, Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    private String buildActionJson(boolean alarm, boolean dnd, boolean nMonth, boolean nWeek, boolean nDay, boolean nOnDay, boolean n1h, boolean n10m, boolean n1m) {
        try {
            JSONObject o = new JSONObject();
            o.put(ActionKeys.ALARM, alarm);
            o.put(ActionKeys.DND, dnd);
            o.put(ActionKeys.NOTIFY_MONTH, nMonth);
            o.put(ActionKeys.NOTIFY_WEEK, nWeek);
            o.put(ActionKeys.NOTIFY_DAY, nDay);
            o.put(ActionKeys.NOTIFY_ON_DAY, nOnDay);
            o.put(ActionKeys.NOTIFY_1H, n1h);
            o.put(ActionKeys.NOTIFY_10M, n10m);
            o.put(ActionKeys.NOTIFY_1M, n1m);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void insertActionTaskChanges(byte[] taskId, String actionJson, Long whenApplyUtcMs) {
        try {
            JSONObject o = new JSONObject(actionJson);
            String[] keys = new String[]{ActionKeys.ALARM, ActionKeys.DND, ActionKeys.NOTIFY_MONTH, ActionKeys.NOTIFY_WEEK, ActionKeys.NOTIFY_DAY, ActionKeys.NOTIFY_ON_DAY, ActionKeys.NOTIFY_1H, ActionKeys.NOTIFY_10M, ActionKeys.NOTIFY_1M};

            long now = System.currentTimeMillis();

            for (String k : keys) {
                if (!o.optBoolean(k, false)) continue;

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                ch.taskId = taskId;
                ch.newTaskId = null;
                ch.type = ActionChangeTypes.on(k);
                ch.createAtUtcMs = now;
                ch.whenApplyUtcMs = whenApplyUtcMs;
                db.taskChangeDao().insert(ch);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FATHER && resultCode == RESULT_OK && data != null) {
            selectedFatherId = data.getByteArrayExtra(TaskPickerActivity.RESULT_TASK_ID);
            String fatherName = data.getStringExtra(TaskPickerActivity.RESULT_TASK_NAME);
            if (fatherName != null) tvFather.setText(fatherName);
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

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.periodic_units_labels, android.R.layout.simple_spinner_dropdown_item);
        spUnit.setAdapter(adapter);
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
            tvList.setText(getString(R.string.empty));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int[] md : yearlyPairs)
            sb.append(String.format("%02d-%02d", md[0], md[1])).append("  ");
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
            return new int[]{m, d};
        } catch (Exception e) {
            return null;
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
            } catch (Exception ignored) {
            }
        }
        return true;
    }
}