package georgii.sytnik.thothtasks.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.AccessGrantEntity;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;
import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.HexBytes;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class ExternalUserManagerActivity extends AppCompatActivity {

    private final List<ExternalUserEntity> users = new ArrayList<>();
    private AppDatabase db;
    private ExternalUserAdapter adapter;

    private byte[] ownerUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_external_user_manager);

        db = AppDatabase.get(this);

        RecyclerView rv = findViewById(R.id.rvExternalUsers);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ExternalUserAdapter(users, new ExternalUserAdapter.Listener() {
            @Override
            public void onToggleBlock(ExternalUserEntity user) {
                toggleBlock(user);
            }

            @Override
            public void onManagePerms(ExternalUserEntity user) {
                openPermissionsDialog(user);
            }
        });

        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ownerUserId = SessionStore.loadLastUserId(this);
        if (ownerUserId == null) return;

        new Thread(() -> {
            users.clear();
            users.addAll(db.externalUserDao().listForOwner(ownerUserId));
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
    }

    private void toggleBlock(ExternalUserEntity user) {
        new Thread(() -> {
            boolean newVal = !user.blocked;
            db.externalUserDao().setBlocked(user.externalId, newVal);
            runOnUiThread(() -> {
                Toast.makeText(this, "Blocked=" + newVal, Toast.LENGTH_SHORT).show();
                load();
            });
        }).start();
    }

    private void openPermissionsDialog(ExternalUserEntity externalUser) {
        if (ownerUserId == null) return;

        new Thread(() -> {
            List<ShareResourceEntity> resources = db.shareResourceDao().listForOwner(ownerUserId);

            HashMap<String, AccessGrantEntity> current = new HashMap<>();
            List<AccessGrantEntity> grants = db.accessGrantDao().grantsForExternal(externalUser.externalId);
            for (AccessGrantEntity g : grants) {
                current.put(HexBytes.hex(g.resourceId), g);
            }

            String[] labels = new String[resources.size()];
            boolean[] checked = new boolean[resources.size()];

            for (int i = 0; i < resources.size(); i++) {
                ShareResourceEntity r = resources.get(i);
                labels[i] = r.name + " (" + r.type + ")";
                AccessGrantEntity g = current.get(HexBytes.hex(r.resourceId));
                checked[i] = (g != null && g.granted && g.revokedAtUtcMs == null);
            }

            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Permisos: " + externalUser.externalUserName)
                        .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                            checked[which] = isChecked;
                        })
                        .setPositiveButton(getString(R.string.save), (d, w) -> {
                            applyGrants(externalUser, resources, checked, current);
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
            });

        }).start();
    }

    private void applyGrants(ExternalUserEntity externalUser,
                             List<ShareResourceEntity> resources,
                             boolean[] checked,
                             HashMap<String, AccessGrantEntity> current) {

        new Thread(() -> {
            long now = System.currentTimeMillis();

            for (int i = 0; i < resources.size(); i++) {
                ShareResourceEntity r = resources.get(i);
                boolean wantGranted = checked[i];

                String key = HexBytes.hex(r.resourceId);
                AccessGrantEntity existing = current.get(key);

                if (wantGranted) {
                    if (existing == null) {
                        AccessGrantEntity g = new AccessGrantEntity();
                        g.grantId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                        g.externalUserId = externalUser.externalId;
                        g.resourceId = r.resourceId;
                        g.granted = true;
                        g.grantedAtUtcMs = now;
                        g.revokedAtUtcMs = null;
                        db.accessGrantDao().upsert(g);
                    } else {
                        existing.granted = true;
                        existing.revokedAtUtcMs = null;
                        db.accessGrantDao().update(existing);
                    }
                } else {
                    if (existing != null && existing.granted && existing.revokedAtUtcMs == null) {
                        existing.granted = false;
                        existing.revokedAtUtcMs = now;
                        db.accessGrantDao().update(existing);
                    }
                }
            }

            runOnUiThread(() -> Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show());
        }).start();
    }
}