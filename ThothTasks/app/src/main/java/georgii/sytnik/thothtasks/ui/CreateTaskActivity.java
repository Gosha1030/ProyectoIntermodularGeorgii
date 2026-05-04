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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

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
import georgii.sytnik.thothtasks.domain.validation.TaskHierarchyValidator;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;

public class CreateTaskActivity extends AppCompatActivity {

    public static final String EXTRA_FATHER_ID = "father_id";
    public static final int REQ_PICK_FATHER = 1001;

    private AppDatabase db;

    private TextInputEditText etName, etStart, etFinish, etTimeM, etPeriodD;
    private CheckBox cbUninterrupted;
    private Spinner spType;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        db = AppDatabase.get(this);

        etName = findViewById(R.id.etName);
        etStart = findViewById(R.id.etStart);
        etFinish = findViewById(R.id.etFinish);
        etTimeM = findViewById(R.id.etTimeM);
        etPeriodD = findViewById(R.id.etPeriodD);
        cbUninterrupted = findViewById(R.id.cbUninterrupted);
        spType = findViewById(R.id.spType);
        typeInfoContainer = findViewById(R.id.typeInfoContainer);

        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTypeInfoUI((String) spType.getSelectedItem());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

// init
        updateTypeInfoUI((String) spType.getSelectedItem());
        btnPickFather = findViewById(R.id.btnPickFather);
        btnCreate = findViewById(R.id.btnCreate);
        tvFather = findViewById(R.id.tvFather);
        btnWhenApply = findViewById(R.id.btnWhenApply);
        tvWhenApply = findViewById(R.id.tvWhenApply);

        btnWhenApply.setOnClickListener(v -> pickWhenApply());

        // Tipos según esquema de BD
        String[] types = new String[] {"Unique", "Daily", "Weekly", "Yearly", "Periodic", "Empty"};
        spType.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, types));

        btnPickFather.setOnClickListener(v -> {
            startActivityForResult(new Intent(this, TaskPickerActivity.class), REQ_PICK_FATHER);
        });

        etStart.setOnClickListener(v -> showTimePicker(etStart));
        etFinish.setOnClickListener(v -> showTimePicker(etFinish));

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

        // Regla: no cruzar medianoche => start < finish si ambos existen
        if (startMin != null && finishMin != null && startMin >= finishMin) {
            Toast.makeText(this, R.string.toast_invalid_time_order, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer timeM = parseInt(etTimeM.getText() != null ? etTimeM.getText().toString().trim() : "");
        Integer periodD = parseInt(etPeriodD.getText() != null ? etPeriodD.getText().toString().trim() : "");

        boolean uninterrupted = cbUninterrupted.isChecked();

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

        String finalName = name;
        String finalDaysOfJson = daysOfJson;
        String finalPeriodicJson = periodicJson;
        new Thread(() -> {
            byte[] userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            UserEntity u = db.userDao().findById(userId);
            if (u == null) return;

            // Si no se seleccionó padre => padre = taskRoot del usuario (pero el root no se muestra)
            byte[] fatherId = selectedFatherId != null ? selectedFatherId : u.taskRoot;

            TaskEntity t = new TaskEntity();
            t.taskId = uuidToBytes(UuidV7.newUuid());
            t.taskFather = fatherId;
            t.taskName = finalName;
            t.type = type;
            t.startTimeMin = startMin;
            t.finishTimeMin = finishMin;
            t.timeM = timeM;
            t.periodD = periodD;
            t.uninterrupted = uninterrupted;
            t.daysOfJson = finalDaysOfJson;
            t.periodicJson = finalPeriodicJson;
            t.state = true;
            t.muted = false;

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

            // Registrar TaskChange create_task con CreateAt epochMillis
            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
            ch.taskId = t.taskId;
            ch.newTaskId = null;
            ch.type = "create_task";
            ch.createAtUtcMs = System.currentTimeMillis();
            ch.whenApplyUtcMs = whenApplyUtcMs; // null => aplica al crear
            db.taskChangeDao().insert(ch);

            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_task_created, Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
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

                whenApplyUtcMs = c2.getTimeInMillis(); // epochMillis UTC
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
        } else {
            // Unique/Daily/Empty: sin TypeInfoBox (por ahora)
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
}