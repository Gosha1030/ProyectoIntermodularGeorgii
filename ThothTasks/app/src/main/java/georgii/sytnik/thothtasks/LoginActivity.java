package georgii.sytnik.thothtasks;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        CheckBox cbAskPassword = findViewById(R.id.cbAskPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUser = prefs.getString("username", "");
        if (!savedUser.isEmpty()) {
            etUsername.setText(savedUser);
        }

        btnLogin.setOnClickListener(v -> {
            String user = etUsername.getText().toString().trim();
            String pass = etPassword.getText().toString();

            if (TextUtils.isEmpty(user) || TextUtils.isEmpty(pass)) {
                Toast.makeText(this, "Rellena usuario y contraseña", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean loginOk = true;

            if (loginOk) {
                boolean askPasswordNextTime = cbAskPassword.isChecked();
                boolean autoLogin = !askPasswordNextTime;

                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("auto_login", autoLogin);
                editor.putString("username", user);
                editor.apply();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show();
            }
        });
    }
}