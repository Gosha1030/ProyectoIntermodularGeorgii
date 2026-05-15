package georgii.sytnik.thothtasks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Random;

import georgii.sytnik.thothtasks.MainActivity;
import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.ui.action.NotificationChannels;

public class SplashActivity extends AppCompatActivity {

    private static final int[] QUOTES = new int[]{
            R.string.splash_quote_1,
            R.string.splash_quote_2,
            R.string.splash_quote_3,
            R.string.splash_quote_4
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        NotificationChannels.ensureCreated(this);

        TextView quote = findViewById(R.id.splashQuote);
        quote.setText(QUOTES[new Random().nextInt(QUOTES.length)]);
        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, 700);
    }

    private void routeNext() {
        byte[] lastUserId = SessionStore.loadLastUserId(this);
        if (lastUserId == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        AppDatabase db = AppDatabase.get(this);
        new Thread(() -> {
            UserEntity u = db.userDao().findById(lastUserId);

            boolean askPassword = true;
            boolean explicitLogout = false;

            if (u != null && u.ajustesJson != null) {
                try {
                    JSONObject obj = new JSONObject(u.ajustesJson);
                    askPassword = obj.optBoolean("askPassword", true);
                    explicitLogout = obj.optBoolean("explicitLogout", false);
                } catch (Exception ignored) {
                }
            }

            boolean goMain = (u != null) && !askPassword && !explicitLogout;

            runOnUiThread(() -> {
                startActivity(new Intent(this, goMain ? MainActivity.class : LoginActivity.class));
                finish();
            });
        }).start();
    }
}