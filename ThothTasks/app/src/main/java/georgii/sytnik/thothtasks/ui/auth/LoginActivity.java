package georgii.sytnik.thothtasks.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import georgii.sytnik.thothtasks.databinding.ActivityLoginBinding;
import georgii.sytnik.thothtasks.ui.main.MainActivity;
import georgii.sytnik.thothtasks.util.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        binding.cbAskPassword.setChecked(sessionManager.isAskPasswordEnabled());

        binding.cbAskPassword.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setAskPasswordEnabled(isChecked));

        binding.btnLogin.setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        String username = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        if (username.isEmpty()) {
            binding.etUsername.setError(getString(georgii.sytnik.thothtasks.R.string.required));
            return;
        }

        // Base mínima: entra al usuario por defecto
        if (sessionManager.isAskPasswordEnabled() && password.isEmpty()) {
            binding.etPassword.setError(getString(georgii.sytnik.thothtasks.R.string.required));
            return;
        }

        sessionManager.login(1L);
        Toast.makeText(this, getString(georgii.sytnik.thothtasks.R.string.welcome, username), Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
