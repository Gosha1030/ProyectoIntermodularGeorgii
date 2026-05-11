package georgii.sytnik.thothtasks.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import georgii.sytnik.thothtasks.R;

public class PlacesTravelsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_places_travels);
        setTitle("Places y Travels");

        MaterialButton btnPlaces = findViewById(R.id.btnPlaces);
        MaterialButton btnTravels = findViewById(R.id.btnTravels);

        btnPlaces.setOnClickListener(v -> startActivity(new Intent(this, PlacesActivity.class)));
        btnTravels.setOnClickListener(v -> startActivity(new Intent(this, TravelsActivity.class)));
    }
}