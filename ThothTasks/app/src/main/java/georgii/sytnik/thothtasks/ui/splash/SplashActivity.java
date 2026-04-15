package georgii.sytnik.thothtasks.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import georgii.sytnik.thothtasks.databinding.ActivitySplashBinding;
import georgii.sytnik.thothtasks.ui.auth.LoginActivity;
import georgii.sytnik.thothtasks.ui.main.MainActivity;
import georgii.sytnik.thothtasks.util.SessionManager;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.tvQuote.setText(getString(georgii.sytnik.thothtasks.R.string.splash_quote));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager sessionManager = new SessionManager(this);
            Intent intent = sessionManager.isLoggedIn()
                    ? new Intent(this, MainActivity.class)
                    : new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        }, 1500);
    }
}