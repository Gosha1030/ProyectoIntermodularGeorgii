package georgii.sytnik.thothtasks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.ui.place.PlacePickerAdapter;

public class PlacePickerActivity extends AppCompatActivity {

    public static final String EXTRA_ALLOW_ANY = "allowAny";
    public static final String EXTRA_RESULT_PLACE_ID = "placeId";
    public static final String EXTRA_RESULT_PLACE_NAME = "placeName";

    private AppDatabase db;
    private final List<PlaceEntity> all = new ArrayList<>();
    private final List<PlaceEntity> filtered = new ArrayList<>();
    private PlacePickerAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_picker);
        setTitle(R.string.place_picker_title);

        db = AppDatabase.get(this);

        boolean allowAny = getIntent().getBooleanExtra(EXTRA_ALLOW_ANY, true);

        RecyclerView rv = findViewById(R.id.rvPlaces);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PlacePickerAdapter(filtered, p -> {
            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_PLACE_ID, p.placeId);
            data.putExtra(EXTRA_RESULT_PLACE_NAME, p.placeName);
            setResult(RESULT_OK, data);
            finish();
        });
        rv.setAdapter(adapter);

        TextInputEditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                applyFilter(s != null ? s.toString() : "");
            }
        });

        new Thread(() -> {
            all.clear();
            all.addAll(db.placeDao().listAll());

            // Prepend getString(R.string.place_picker_any) option as a fake place
            if (allowAny) {
                PlaceEntity any = new PlaceEntity();
                any.placeId = null;
                any.placeName = getString(R.string.place_picker_any);
                any.googleMapsDataJson = null;
                all.add(0, any);
            }

            runOnUiThread(() -> {
                applyFilter("");
            });
        }).start();
    }

    private void applyFilter(String q) {
        String qq = q == null ? "" : q.trim().toLowerCase();
        filtered.clear();
        if (qq.isEmpty()) {
            filtered.addAll(all);
        } else {
            for (PlaceEntity p : all) {
                if (p.placeName != null && p.placeName.toLowerCase().contains(qq)) {
                    filtered.add(p);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }
}