package georgii.sytnik.thothtasks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.MainActivity;
import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.security.PasswordHash;
import georgii.sytnik.thothtasks.security.SessionSecrets;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class LoginActivity extends AppCompatActivity {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private AppDatabase db;
    private MaterialAutoCompleteTextView actvUsername;
    private TextInputEditText etPassword;
    private CheckBox cbAskPassword;
    private MaterialButton btnLogin;
    private Runnable pendingLookup;
    private int lookupSeq = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = AppDatabase.get(this);

        actvUsername = findViewById(R.id.actvUsername);
        etPassword = findViewById(R.id.etPassword);
        cbAskPassword = findViewById(R.id.cbAskPassword);
        btnLogin = findViewById(R.id.btnLogin);
        loadUsernamesIntoAutocomplete();

        wireUsernameListeners();

        btnLogin.setOnClickListener(v -> onLoginClicked());
    }

    private void loadUsernamesIntoAutocomplete() {
        new Thread(() -> {
            List<UserEntity> users = db.userDao().getAllUsers();
            List<String> names = new ArrayList<>();
            for (UserEntity u : users) names.add(u.userName);

            runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        names
                );
                actvUsername.setAdapter(adapter);
            });
        }).start();
    }

    private void onLoginClicked() {
        String userName = actvUsername.getText() != null ? actvUsername.getText().toString().trim() : "";
        String pass = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (TextUtils.isEmpty(userName)) {
            Toast.makeText(this, R.string.toast_empty_username, Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            UserEntity existing = db.userDao().findByUserName(userName);

            if (existing == null) {
                UserEntity created = createNewUser(userName, pass);
                db.userDao().insert(created);

                TaskEntity root = createTaskRoot();
                db.taskDao().insert(root);

                created.taskRoot = root.taskId;
                db.userDao().update(created);

                persistSessionAndSettings(created, cbAskPassword.isChecked());

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                ch.taskId = root.taskId;
                ch.newTaskId = null;
                ch.type = "create_task";
                ch.createAtUtcMs = System.currentTimeMillis();
                ch.whenApplyUtcMs = null;
                db.taskChangeDao().insert(ch);

                if (!pass.isEmpty()) {
                    SessionSecrets.setPassword(pass.toCharArray());
                } else {
                    SessionSecrets.clear();
                }

                runOnUiThread(() -> Toast.makeText(this, R.string.toast_user_created, Toast.LENGTH_SHORT).show());
                goMain();
                return;
            }

            boolean allowPasswordless = isSameAsLastSessionUser(existing) && isAskPasswordDisabled(existing);

            if (pass.isEmpty()) {
                if (!allowPasswordless) {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.toast_password_required, Toast.LENGTH_SHORT).show()
                    );
                    return;
                }
                SessionSecrets.clear();
            } else {
                boolean ok = PasswordHash.verify(pass.toCharArray(), existing.password);
                if (!ok) {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.toast_wrong_password, Toast.LENGTH_SHORT).show()
                    );
                    return;
                }
                SessionSecrets.setPassword(pass.toCharArray());
            }

            persistSessionAndSettings(existing, cbAskPassword.isChecked());
            goMain();
        }).start();
    }

    private void goMain() {
        runOnUiThread(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private UserEntity createNewUser(String userName, String pass) {
        UserEntity u = new UserEntity();
        u.userId = UuidBytes.uuidToBytes(UuidV7.newUuid());
        u.userName = userName;
        u.password = PasswordHash.hashToStoredString(pass.toCharArray());
        u.type = "USER";
        u.passwordRequired = true;
        u.confirmRequired = true;
        u.ip = guessLocalIp();
        u.port = null;
        u.ajustesJson = null;
        u.taskRoot = new byte[16];
        return u;
    }

    private TaskEntity createTaskRoot() {
        TaskEntity t = new TaskEntity();
        t.taskId = UuidBytes.uuidToBytes(UuidV7.newUuid());
        t.taskFather = null;
        t.taskName = "ROOT";
        t.type = "Empty";
        t.state = true;
        t.muted = false;
        return t;
    }

    private String guessLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "0.0.0.0";
        }
    }

    private void persistSessionAndSettings(UserEntity user, boolean askPasswordChecked) {
        SessionStore.saveLastUserId(this, user.userId);

        try {
            JSONObject obj = user.ajustesJson != null ? new JSONObject(user.ajustesJson) : new JSONObject();
            obj.put("askPassword", askPasswordChecked);
            obj.put("lastLoginUtcMs", System.currentTimeMillis());
            obj.put("explicitLogout", false);

            user.ajustesJson = obj.toString();
            db.userDao().update(user);
        } catch (Exception e) {
        }
    }

    private void wireUsernameListeners() {
        actvUsername.setOnItemClickListener((parent, view, position, id) -> {
            String name = actvUsername.getText() != null ? actvUsername.getText().toString().trim() : "";
            syncAskPasswordCheckbox(name);
            if (etPassword.getText() != null) etPassword.getText().clear();
        });

        actvUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String name = s != null ? s.toString().trim() : "";

                if (pendingLookup != null) uiHandler.removeCallbacks(pendingLookup);

                final int requestId = ++lookupSeq;
                pendingLookup = () -> {
                    if (requestId != lookupSeq) return;
                    syncAskPasswordCheckbox(name);
                };

                uiHandler.postDelayed(pendingLookup, 300);
            }
        });
    }

    private void syncAskPasswordCheckbox(String userName) {
        if (userName == null || userName.isEmpty()) {
            runOnUiThread(() -> {
                cbAskPassword.setChecked(true);
                cbAskPassword.setEnabled(true);
                cbAskPassword.setAlpha(1f);
            });
            return;
        }

        new Thread(() -> {
            UserEntity u = db.userDao().findByUserName(userName);

            boolean askPassword = true;
            if (u != null && u.ajustesJson != null) {
                try {
                    JSONObject obj = new JSONObject(u.ajustesJson);
                    askPassword = obj.optBoolean("askPassword", true);
                } catch (Exception ignored) {
                }
            }

            final boolean finalAsk = askPassword;
            final boolean editable = (u == null);
            runOnUiThread(() -> {
                cbAskPassword.setChecked(finalAsk);
                cbAskPassword.setEnabled(editable);
                cbAskPassword.setAlpha(editable ? 1f : 0.6f);
            });
        }).start();
    }

    private boolean isAskPasswordDisabled(UserEntity user) {
        if (user == null || user.ajustesJson == null) return false;
        try {
            JSONObject obj = new JSONObject(user.ajustesJson);
            return !obj.optBoolean("askPassword", true);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isSameAsLastSessionUser(UserEntity user) {
        if (user == null || user.userId == null) return false;
        byte[] last = SessionStore.loadLastUserId(this);
        if (last == null || last.length != user.userId.length) return false;

        for (int i = 0; i < last.length; i++) {
            if (last[i] != user.userId[i]) return false;
        }
        return true;
    }
}
