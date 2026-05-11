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
import java.util.UUID;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.AccessGrantEntity;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;
import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;

public class ExternalUserManagerActivity extends AppCompatActivity {

    private AppDatabase db;

    private final List<ExternalUserEntity> users = new ArrayList<>();
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

    /**
     * Grants: ExternalUser -> ShareResource
     * UI: multi-choice dialog with all ShareResources of this owner.
     */
    private void openPermissionsDialog(ExternalUserEntity externalUser) {
        if (ownerUserId == null) return;

        new Thread(() -> {
            List<ShareResourceEntity> resources = db.shareResourceDao().listForOwner(ownerUserId);

            // Current grants map by ResourceId hex
            HashMap<String, AccessGrantEntity> current = new HashMap<>();
            List<AccessGrantEntity> grants = db.accessGrantDao().grantsForExternal(externalUser.externalId);
            for (AccessGrantEntity g : grants) {
                current.put(hex(g.resourceId), g);
            }

            String[] labels = new String[resources.size()];
            boolean[] checked = new boolean[resources.size()];

            for (int i = 0; i < resources.size(); i++) {
                ShareResourceEntity r = resources.get(i);
                labels[i] = r.name + " (" + r.type + ")";
                AccessGrantEntity g = current.get(hex(r.resourceId));
                checked[i] = (g != null && g.granted && g.revokedAtUtcMs == null);
            }

            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Permisos: " + externalUser.externalUserName)
                        .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                            // store in checked[] only; apply on Save
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

                String key = hex(r.resourceId);
                AccessGrantEntity existing = current.get(key);

                if (wantGranted) {
                    if (existing == null) {
                        AccessGrantEntity g = new AccessGrantEntity();
                        g.grantId = uuidToBytes(UuidV7.newUuid());
                        g.externalUserId = externalUser.externalId;
                        g.resourceId = r.resourceId;
                        g.granted = true;
                        g.grantedAtUtcMs = now;
                        g.revokedAtUtcMs = null;
                        db.accessGrantDao().upsert(g);
                    } else {
                        existing.granted = true;
                        existing.revokedAtUtcMs = null;
                        // keep original grantedAt if you want, or update:
                        // existing.grantedAtUtcMs = now;
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

    // ---- utils ----
    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[] {
                (byte)(msb >>> 56), (byte)(msb >>> 48), (byte)(msb >>> 40), (byte)(msb >>> 32),
                (byte)(msb >>> 24), (byte)(msb >>> 16), (byte)(msb >>>  8), (byte)(msb),
                (byte)(lsb >>> 56), (byte)(lsb >>> 48), (byte)(lsb >>> 40), (byte)(lsb >>> 32),
                (byte)(lsb >>> 24), (byte)(lsb >>> 16), (byte)(lsb >>>  8), (byte)(lsb)
        };
    }
}