package georgii.sytnik.thothtasks.ui;

import static georgii.sytnik.thothtasks.util.HexBytes.hex;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.db.entities.TravelEntity;
import georgii.sytnik.thothtasks.net.MessageCodec;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.ui.travel.TravelAdapter;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class TravelsActivity extends AppCompatActivity {

    public static final String EXTRA_PREFILL_START = "prefillStartPlaceId";
    public static final String EXTRA_PREFILL_FINISH = "prefillFinishPlaceId";
    public static final String EXTRA_AUTO_OPEN = "autoOpenTravelEditor";
    private final List<TravelEntity> travels = new ArrayList<>();
    private final HashMap<String, String> placeNameByIdHex = new HashMap<>();
    private AppDatabase db;
    private TravelAdapter adapter;
    private TravelEntity editing;
    private byte[] startPlaceId;
    private byte[] finishPlaceId;
    private TextView tvStart;
    private final ActivityResultLauncher<Intent> pickStartLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                    startPlaceId = res.getData().getByteArrayExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_ID);
                    String name = res.getData().getStringExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_NAME);
                    if (tvStart != null) tvStart.setText(name != null ? name : "(?)");
                }
            });
    private TextView tvFinish;
    private final ActivityResultLauncher<Intent> pickFinishLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                    finishPlaceId = res.getData().getByteArrayExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_ID);
                    String name = res.getData().getStringExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_NAME);
                    if (tvFinish != null) tvFinish.setText(name != null ? name : "(?)");
                }
            });
    private boolean pendingAutoOpen = false;
    private byte[] prefillStartId = null;
    private byte[] prefillFinishId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travels);
        setTitle(R.string.travels_title);

        pendingAutoOpen = getIntent().getBooleanExtra(EXTRA_AUTO_OPEN, false);
        prefillStartId = getIntent().getByteArrayExtra(EXTRA_PREFILL_START);
        prefillFinishId = getIntent().getByteArrayExtra(EXTRA_PREFILL_FINISH);

        db = AppDatabase.get(this);

        RecyclerView rv = findViewById(R.id.rvTravels);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TravelAdapter(travels, placeNameByIdHex, new TravelAdapter.Listener() {
            @Override
            public void onClick(TravelEntity t) {
                editTravelDialog(t);
            }

            @Override
            public void onDelete(TravelEntity t) {
                confirmDelete(t);
            }
        });
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAddTravel);
        fab.setOnClickListener(v -> createTravelDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        new Thread(() -> {
            placeNameByIdHex.clear();
            List<PlaceEntity> places = db.placeDao().listAll();
            for (PlaceEntity p : places) {
                placeNameByIdHex.put(hex(p.placeId), p.placeName);
            }

            travels.clear();
            travels.addAll(db.travelDao().listAll());

            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();

                if (pendingAutoOpen && prefillStartId != null && prefillFinishId != null) {
                    pendingAutoOpen = false;
                    editing = null;
                    startPlaceId = prefillStartId;
                    finishPlaceId = prefillFinishId;
                    openTravelEditor(null);
                }
            });

        }).start();
    }

    private void createTravelDialog() {
        new Thread(() -> {
            List<PlaceEntity> places = db.placeDao().listAll();
            if (places.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, R.string.toast_create_places_first, Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> openTravelEditor(null));
        }).start();
    }

    private void editTravelDialog(TravelEntity t) {
        openTravelEditor(t);
    }

    private void openTravelEditor(@Nullable TravelEntity t) {
        this.editing = t;

        startPlaceId = (t != null) ? t.startPlaceId : null;
        finishPlaceId = (t != null) ? t.finishPlaceId : null;

        AlertDialog dlg;
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_travel_edit, null);

        MaterialButton btnPickStart = v.findViewById(R.id.btnPickStart);
        MaterialButton btnPickFinish = v.findViewById(R.id.btnPickFinish);
        tvStart = v.findViewById(R.id.tvStart);
        tvFinish = v.findViewById(R.id.tvFinish);

        TextInputEditText etType = v.findViewById(R.id.etType);
        TextInputEditText etTimeM = v.findViewById(R.id.etTimeM);
        TextInputEditText etUserTimeM = v.findViewById(R.id.etUserTimeM);
        TextInputEditText etGoogleTimeM = v.findViewById(R.id.etGoogleTimeM);
        TextInputEditText etGoogleData = v.findViewById(R.id.etGoogleData);

        if (t != null) {
            etType.setText(t.type != null ? t.type : "");
            etTimeM.setText(String.valueOf(t.timeM));
            etUserTimeM.setText(t.userTimeM != null ? String.valueOf(t.userTimeM) : "");
            etGoogleTimeM.setText(t.googleTimeM != null ? String.valueOf(t.googleTimeM) : "");
            etGoogleData.setText(t.googleDataJson != null ? t.googleDataJson : "");
        }

        tvStart.setText(nameOfPlace(startPlaceId));
        tvFinish.setText(nameOfPlace(finishPlaceId));

        btnPickStart.setOnClickListener(b -> launchPickStart());
        btnPickFinish.setOnClickListener(b -> launchPickFinish());

        dlg = new AlertDialog.Builder(this)
                .setTitle(t == null ? getString(R.string.create) : getString(R.string.save))
                .setView(v)
                .setPositiveButton(getString(R.string.save), null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        AlertDialog finalDlg = dlg;
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            if (startPlaceId == null || finishPlaceId == null) {
                Toast.makeText(this, R.string.toast_travel_start_finish_required, Toast.LENGTH_SHORT).show();
                return;
            }

            int timeM;
            try {
                String s = etTimeM.getText() != null ? etTimeM.getText().toString().trim() : "";
                timeM = Integer.parseInt(s);
                if (timeM <= 0) throw new Exception();
            } catch (Exception e) {
                Toast.makeText(this, R.string.toast_travel_time_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            String typeStr = etType.getText() != null ? etType.getText().toString().trim() : null;
            if (typeStr != null && typeStr.isEmpty()) typeStr = null;

            Integer userTime = parseIntOrNull(etUserTimeM);
            Integer googleTime = parseIntOrNull(etGoogleTimeM);

            String googleData = etGoogleData.getText() != null ? etGoogleData.getText().toString().trim() : null;
            if (googleData != null && googleData.isEmpty()) googleData = null;

            saveTravel(finalDlg, typeStr, timeM, userTime, googleTime, googleData);
        });
    }

    private void launchPickStart() {
        Intent i = new Intent(this, PlacePickerActivity.class);
        i.putExtra(PlacePickerActivity.EXTRA_ALLOW_ANY, false);
        pickStartLauncher.launch(i);
    }

    private void launchPickFinish() {
        Intent i = new Intent(this, PlacePickerActivity.class);
        i.putExtra(PlacePickerActivity.EXTRA_ALLOW_ANY, false);
        pickFinishLauncher.launch(i);
    }

    private String nameOfPlace(byte[] placeId) {
        if (placeId == null) return getString(R.string.not_set);
        return placeNameByIdHex.getOrDefault(hex(placeId), "(?)");
    }

    private Integer parseIntOrNull(TextInputEditText et) {
        try {
            String s = et.getText() != null ? et.getText().toString().trim() : "";
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveTravel(AlertDialog dlg, String type, int timeM, Integer userTimeM, Integer googleTimeM, String googleDataJson) {
        new Thread(() -> {
            try {
                TravelEntity tr = (editing == null) ? new TravelEntity() : editing;

                if (editing == null) {
                    tr.travelId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                }
                tr.startPlaceId = startPlaceId;
                tr.finishPlaceId = finishPlaceId;
                tr.type = type;
                tr.timeM = timeM;
                tr.userTimeM = userTimeM;
                tr.googleTimeM = googleTimeM;
                tr.googleDataJson = googleDataJson;

                if (editing == null) db.travelDao().insert(tr);
                else db.travelDao().update(tr);

                runOnUiThread(() -> {
                    dlg.dismiss();
                    Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                    load();
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.toast_travel_save_error, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmDelete(TravelEntity t) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_delete_generic_title))
                .setMessage(nameOfPlace(t.startPlaceId) + " → " + nameOfPlace(t.finishPlaceId))
                .setPositiveButton(R.string.delete, (d, w) -> deleteTravel(t))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteTravel(TravelEntity t) {
        new Thread(() -> {
            db.travelDao().delete(t.travelId);
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                load();
            });
        }).start();
    }
}