package georgii.sytnik.thothtasks.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.domain.place.PlaceService;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.ui.place.PlaceAdapter;

public class PlacesActivity extends AppCompatActivity {

    private AppDatabase db;
    private final List<PlaceEntity> places = new ArrayList<>();
    private PlaceAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_places);
        setTitle("Places");

        db = AppDatabase.get(this);

        RecyclerView rv = findViewById(R.id.rvPlaces);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PlaceAdapter(places, new PlaceAdapter.Listener() {
            @Override public void onClick(PlaceEntity p) { editPlaceDialog(p); }
            @Override public void onDelete(PlaceEntity p) { deletePlace(p); }
        });
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> createPlaceDialog());
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        new Thread(() -> {
            places.clear();
            places.addAll(db.placeDao().listAll());
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
    }

    private void createPlaceDialog() {
        TextInputEditText etName = new TextInputEditText(this);
        etName.setHint("PlaceName");

        new AlertDialog.Builder(this)
                .setTitle("New Place")
                .setView(etName)
                .setPositiveButton("Save", (d,w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    saveNewPlace(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveNewPlace(String name) {
        new Thread(() -> {
            try {
                PlaceEntity existing = db.placeDao().findByName(name);
                if (existing != null) {
                    runOnUiThread(() -> Toast.makeText(this, "Ya existe", Toast.LENGTH_SHORT).show());
                    return;
                }
                PlaceEntity p = new PlaceEntity();
                p.placeId = georgii.sytnik.thothtasks.ui.TaskManagerActivity.uuidToBytes(UuidV7.newUuid());
                p.placeName = name;
                p.googleMapsDataJson = null;
                db.placeDao().insert(p);
                runOnUiThread(() -> { Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show(); load(); });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void editPlaceDialog(PlaceEntity p) {
        TextInputEditText etName = new TextInputEditText(this);
        etName.setText(p.placeName);

        new AlertDialog.Builder(this)
                .setTitle("Edit Place")
                .setView(etName)
                .setPositiveButton("Save", (d,w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    updatePlace(p, name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updatePlace(PlaceEntity p, String name) {
        new Thread(() -> {
            p.placeName = name;
            db.placeDao().update(p);
            runOnUiThread(() -> { Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show(); load(); });
        }).start();
    }

    private void deletePlace(PlaceEntity p) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Place?")
                .setMessage(p.placeName)
                .setPositiveButton(R.string.delete, (d,w) -> {
                    new Thread(() -> {
                        PlaceService.deletePlaceCascade(db, p.placeId);
                        runOnUiThread(() -> { Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show(); load(); });
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}