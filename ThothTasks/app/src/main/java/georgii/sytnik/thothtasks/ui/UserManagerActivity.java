package georgii.sytnik.thothtasks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;
import georgii.sytnik.thothtasks.db.entities.SyncStateEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.net.AccessClient;
import georgii.sytnik.thothtasks.net.MessageCodec;
import georgii.sytnik.thothtasks.net.SyncClient;
import georgii.sytnik.thothtasks.net.UdpClient;
import georgii.sytnik.thothtasks.net.VersionClient;
import georgii.sytnik.thothtasks.security.PasswordHash;
import georgii.sytnik.thothtasks.security.SessionSecrets;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.HexBytes;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class UserManagerActivity extends AppCompatActivity {

    private AppDatabase db;

    private final List<SimpleRowAdapter.Row> localRows = new ArrayList<>();
    private final List<SimpleRowAdapter.Row> sourceRows = new ArrayList<>();
    private final List<ShareResourceEntity> locals = new ArrayList<>();
    private final List<ExternalSourceEntity> sources = new ArrayList<>();

    private SimpleRowAdapter localAdapter;
    private ExternalSourceAdapter sourceAdapter;

    private byte[] currentUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_manager);

        db = AppDatabase.get(this);

        RecyclerView rvLocals = findViewById(R.id.rvLocals);
        RecyclerView rvSources = findViewById(R.id.rvSources);

        rvLocals.setLayoutManager(new LinearLayoutManager(this));
        rvSources.setLayoutManager(new LinearLayoutManager(this));

        localAdapter = new SimpleRowAdapter(localRows, new SimpleRowAdapter.Listener() {
            @Override public void onClick(int position) { editLocal(locals.get(position)); }
            @Override public void onDelete(int position) { deleteLocal(locals.get(position)); }
        });
        rvLocals.setAdapter(localAdapter);


        sourceAdapter = new ExternalSourceAdapter(sources, new ExternalSourceAdapter.Listener() {

            @Override
            public void onToggle(ExternalSourceEntity src, boolean included) {
                new Thread(() -> {
                    db.externalSourceDao().setIncluded(src.sourceId, included);
                    runOnUiThread(UserManagerActivity.this::load);
                }).start();
            }

            @Override
            public void onDelete(ExternalSourceEntity src) { deleteSource(src); }

            @Override
            public void onCheck(ExternalSourceEntity src) { checkUpdate(src); }

            @Override
            public void onSync(ExternalSourceEntity src) { syncNow(src); }
        });
        rvSources.setAdapter(sourceAdapter);


        MaterialButton btnAddLocal = findViewById(R.id.btnAddLocal);
        MaterialButton btnAddSource = findViewById(R.id.btnAddSource);
        MaterialButton btnExternalUserManager = findViewById(R.id.btnExternalUserManager);

        btnExternalUserManager.setOnClickListener(v ->
                startActivity(new Intent(this, ExternalUserManagerActivity.class)));

        btnAddLocal.setOnClickListener(v -> createLocalDialog());
        btnAddSource.setOnClickListener(v -> addSourceDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        currentUserId = SessionStore.loadLastUserId(this);
        new Thread(() -> {
            locals.clear();
            sources.clear();
            localRows.clear();
            sourceRows.clear();

            if (currentUserId != null) {
                UserEntity u = db.userDao().findById(currentUserId);
                if (u != null) {
                    locals.addAll(db.shareResourceDao().listForOwner(u.userId));
                }
            }
            sources.addAll(db.externalSourceDao().listAll());

            for (ShareResourceEntity r : locals) {
                String sub = r.type + " • port=" + (r.port != null ? r.port : "-")
                        + " • active=" + r.active;
                localRows.add(new SimpleRowAdapter.Row(r.name, sub));
            }
            for (ExternalSourceEntity s : sources) {
                String sub = s.ip + ":" + s.port + " • blocked=" + s.blocked;
                sourceRows.add(new SimpleRowAdapter.Row(s.displayName, sub));
            }

            runOnUiThread(() -> {
                localAdapter.notifyDataSetChanged();
                sourceAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    // -------- Locals --------

    private void createLocalDialog() {
        // v1: local root = current user's TaskRoot (later: pick subtree from tree)
        if (currentUserId == null) return;

        new Thread(() -> {
            UserEntity u = db.userDao().findById(currentUserId);
            if (u == null) return;

            runOnUiThread(() -> {
                TextInputEditText etName = new TextInputEditText(this);
                etName.setHint(getString(R.string.field_name));

                TextInputEditText etPort = new TextInputEditText(this);
                etPort.setHint(getString(R.string.field_port));
                etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

                android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
                layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                layout.setPadding(48, 24, 48, 0);
                layout.addView(etName);
                layout.addView(etPort);

                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.btn_add_local))
                        .setView(layout)
                        .setPositiveButton(getString(R.string.save), (d, w) -> {
                            String name = etName.getText() != null ? etName.getText().toString().trim() : "Local";
                            Integer port = null;
                            try { port = Integer.parseInt(etPort.getText().toString().trim()); } catch (Exception ignored) {}
                            saveNewLocal(u, name, port);
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
            });
        }).start();
    }

    private void saveNewLocal(UserEntity owner, String name, Integer port) {
        new Thread(() -> {
            ShareResourceEntity r = new ShareResourceEntity();
            r.resourceId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            r.ownerUserId = owner.userId;
            r.type = "LOCAL";
            r.name = name;
            r.rootTaskId = owner.taskRoot;
            r.port = port;
            r.passwordRequired = true;   // same password as user
            r.confirmRequired = true;
            r.active = true;

            db.shareResourceDao().insert(r);

            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                load();
            });
        }).start();
    }

    private void editLocal(ShareResourceEntity r) {
        TextInputEditText etName = new TextInputEditText(this);
        etName.setText(r.name);

        TextInputEditText etPort = new TextInputEditText(this);
        etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPort.setText(r.port != null ? String.valueOf(r.port) : "");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.addView(etName);
        layout.addView(etPort);

        new AlertDialog.Builder(this)
                .setTitle("Edit Local")
                .setView(layout)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : r.name;
                    Integer port = r.port;
                    try { port = Integer.parseInt(etPort.getText().toString().trim()); } catch (Exception ignored) {}
                    updateLocal(r, name, port);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void updateLocal(ShareResourceEntity r, String name, Integer port) {
        new Thread(() -> {
            r.name = name;
            r.port = port;
            db.shareResourceDao().update(r);
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                load();
            });
        }).start();
    }

    private void deleteLocal(ShareResourceEntity r) {
        new Thread(() -> {
            db.shareResourceDao().delete(r.resourceId);
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                load();
            });
        }).start();
    }

    // -------- External Sources (v1 store only) --------

    private void addSourceDialog() {
        TextInputEditText etName = new TextInputEditText(this);
        etName.setHint(getString(R.string.field_name));

        TextInputEditText etIp = new TextInputEditText(this);
        etIp.setHint(getString(R.string.field_ip));

        TextInputEditText etPort = new TextInputEditText(this);
        etPort.setHint(getString(R.string.field_port));
        etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        TextInputEditText etResource = new TextInputEditText(this);
        etResource.setHint(getString(R.string.hint_resource_id));

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.addView(etName);
        layout.addView(etIp);
        layout.addView(etPort);
        layout.addView(etResource);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_add_source))
                .setView(layout)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "Source";
                    String ip = etIp.getText() != null ? etIp.getText().toString().trim() : "";
                    int port = 0;
                    try { port = Integer.parseInt(etPort.getText().toString().trim()); } catch (Exception ignored) {}
                    String resHex = etResource.getText() != null ? etResource.getText().toString().trim() : "";
                    addSource(name, ip, port, resHex);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void addSource(String displayName, String ip, int port, String resourceHex) {
        if (ip.isEmpty() || port <= 0 || resourceHex.isEmpty()) return;

        ensureNetworkPassword(pwd -> new Thread(() -> {
            try {
                byte[] ownerUserId = SessionStore.loadLastUserId(this);
                if (ownerUserId == null) return;
                UserEntity owner = db.userDao().findById(ownerUserId);
                if (owner == null) return;

                // externalName: lo que el owner verá/guardará como ExternalUserName
                String externalName = owner.userName != null ? owner.userName : "external";

                // 1) Request access
                AccessClient.Result ar = AccessClient.requestAccess(ip, port, resourceHex, externalName, 2500);
                if (!ar.granted) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Acceso denegado: " + ar.reason, Toast.LENGTH_LONG).show()
                    );
                    return;
                }

                // 2) Build startDay (today 00:00)
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                long startDay = c.getTimeInMillis();

                // 3) Request summary 30 days
                ExternalSourceEntity temp = new ExternalSourceEntity();
                temp.ip = ip;
                temp.port = port;
                temp.resourceId = HexBytes.hexToBytes(resourceHex);

                JSONObject summaryBody = georgii.sytnik.thothtasks.net.ScheduleSummaryClientSecure.requestSummarySecure(
                        this,
                        temp,
                        externalName,
                        startDay,
                        30,
                        3000
                );

                // 4) Conflict check (A: if conflict => do not add)
                boolean conflict = georgii.sytnik.thothtasks.domain.ConflictChecker
                        .hasConflictWithLocal(db, owner.taskRoot, startDay, summaryBody);

                if (conflict) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Conflicto de horario: no se puede añadir.", Toast.LENGTH_LONG).show()
                    );
                    return;
                }

                // 5) SYNC with progress dialog
                final android.util.AtomicFile dummy = null; // no-op, just to avoid lint (can ignore)
                final java.util.concurrent.atomic.AtomicReference<androidx.appcompat.app.AlertDialog> dlgRef = new java.util.concurrent.atomic.AtomicReference<>();
                final java.util.concurrent.atomic.AtomicReference<android.widget.TextView> tvRef = new java.util.concurrent.atomic.AtomicReference<>();

                runOnUiThread(() -> {
                    android.widget.TextView tv = new android.widget.TextView(this);
                    tv.setPadding(48, 32, 48, 32);
                    tv.setText("Sync: 0/0");
                    androidx.appcompat.app.AlertDialog dlg = new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Sincronizando…")
                            .setView(tv)
                            .setCancelable(false)
                            .show();
                    dlgRef.set(dlg);
                    tvRef.set(tv);
                });

                SyncClient.SyncResult sr = SyncClient.syncResource(
                        ip, port, resourceHex, externalName, 0, 1200,
                        (received, total) -> runOnUiThread(() -> {
                            android.widget.TextView tv = tvRef.get();
                            if (tv != null) tv.setText("Sync: " + received + "/" + total);
                        })
                );

                runOnUiThread(() -> {
                    androidx.appcompat.app.AlertDialog dlg = dlgRef.get();
                    if (dlg != null) dlg.dismiss();
                });

                // 6) Insert ExternalSource FIRST (so Importer can update ImportedRootTaskId + SyncState)
                ExternalSourceEntity src = new ExternalSourceEntity();
                src.sourceId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                src.displayName = displayName;
                src.ip = ip;
                src.port = port;
                src.resourceId = HexBytes.hexToBytes(resourceHex);
                src.remotePubKeyB64 = null;
                src.blocked = false;
                src.includedInSchedule = true;
                src.importedRootTaskId = null;
                db.externalSourceDao().insert(src);

                // 7) Import tasks & changes under TaskRoot as [Imported] displayName
                georgii.sytnik.thothtasks.domain.sync.Importer.importSyncResult(
                        db,
                        ownerUserId,
                        src,
                        sr.tasks,
                        sr.taskChanges,
                        sr.remoteVersion
                );

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                    load();
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start());
    }

    private void deleteSource(ExternalSourceEntity s) {
        new Thread(() -> {
            db.externalSourceDao().delete(s.sourceId);
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                load();
            });
        }).start();
    }

    private void checkUpdate(ExternalSourceEntity src) {
        ensureNetworkPassword(pwd -> new Thread(() -> {
            try {
                byte[] ownerUserId = SessionStore.loadLastUserId(this);
                UserEntity owner = db.userDao().findById(ownerUserId);
                String externalName = owner != null && owner.userName != null ? owner.userName : "external";

                long remoteVersion = VersionClient.requestRemoteVersion(
                        src.ip, src.port, MessageCodec.hex(src.resourceId), externalName, 2500
                );

                String peerKey = src.ip + ":" + src.port;
                SyncStateEntity st = db.syncStateDao().find(peerKey, src.resourceId);

                long applied = (st != null) ? st.lastAppliedVersion : 0;
                boolean hasUpdate = remoteVersion > applied;

                if (st == null) {
                    st = new SyncStateEntity();
                    st.syncId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                    st.peerKey = peerKey;
                    st.resourceId = src.resourceId;
                }
                long now = System.currentTimeMillis();
                st.lastSeenUtcMs = now;
                st.lastRemoteVersion = remoteVersion;
                st.hasUpdate = hasUpdate;
                db.syncStateDao().upsert(st);

                runOnUiThread(() ->
                        Toast.makeText(this, hasUpdate ? "Update disponible" : "Ya actualizado", Toast.LENGTH_SHORT).show()
                );

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Check failed", Toast.LENGTH_SHORT).show());
            }
        }).start());
    }

    private void syncNow(ExternalSourceEntity src) {
        ensureNetworkPassword(pwd -> new Thread(() -> {
            try {
                byte[] ownerUserId = SessionStore.loadLastUserId(this);
                UserEntity owner = db.userDao().findById(ownerUserId);
                String externalName = owner != null && owner.userName != null ? owner.userName : "external";

                String peerKey = src.ip + ":" + src.port;
                SyncStateEntity st = db.syncStateDao().find(peerKey, src.resourceId);
                long since = (st != null) ? st.lastAppliedVersion : 0;

                // Progress dialog
                final java.util.concurrent.atomic.AtomicReference<androidx.appcompat.app.AlertDialog> dlgRef = new java.util.concurrent.atomic.AtomicReference<>();
                final java.util.concurrent.atomic.AtomicReference<android.widget.TextView> tvRef = new java.util.concurrent.atomic.AtomicReference<>();

                runOnUiThread(() -> {
                    android.widget.TextView tv = new android.widget.TextView(this);
                    tv.setPadding(48, 32, 48, 32);
                    tv.setText("Sync: 0/0");
                    androidx.appcompat.app.AlertDialog dlg = new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Sincronizando…")
                            .setView(tv)
                            .setCancelable(false)
                            .show();
                    dlgRef.set(dlg);
                    tvRef.set(tv);
                });

                SyncClient.SyncResult sr = SyncClient.syncResource(
                        src.ip, src.port, MessageCodec.hex(src.resourceId), externalName, since, 1200,
                        (received, total) -> runOnUiThread(() -> {
                            android.widget.TextView tv = tvRef.get();
                            if (tv != null) tv.setText("Sync: " + received + "/" + total);
                        })
                );

                runOnUiThread(() -> {
                    androidx.appcompat.app.AlertDialog dlg = dlgRef.get();
                    if (dlg != null) dlg.dismiss();
                });

                // Import (keeps remote ids)
                georgii.sytnik.thothtasks.domain.sync.Importer.importSyncResult(
                        db, ownerUserId, src, sr.tasks, sr.taskChanges, sr.remoteVersion
                );

                runOnUiThread(() -> {
                    Toast.makeText(this, "Sync OK", Toast.LENGTH_SHORT).show();
                    load();
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Sync failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start());
    }

    // ---- utils ----

    private interface PasswordOkCallback { void onOk(char[] pwd); }

    private void ensureNetworkPassword(PasswordOkCallback cb) {
        new Thread(() -> {
            byte[] ownerUserId = SessionStore.loadLastUserId(this);
            if (ownerUserId == null) return;
            UserEntity owner = db.userDao().findById(ownerUserId);
            if (owner == null) return;

            char[] cached = SessionSecrets.getPassword();
            if (cached != null && cached.length > 0) {
                runOnUiThread(() -> cb.onOk(cached));
                return;
            }

            runOnUiThread(() -> showPasswordDialog(owner, cb));
        }).start();
    }

    private void showPasswordDialog(UserEntity owner, PasswordOkCallback cb) {
        TextInputEditText et = new TextInputEditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setHint("Password");

        new AlertDialog.Builder(this)
                .setTitle("Introduce password para habilitar red")
                .setView(et)
                .setPositiveButton("OK", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Introduce password para habilitar red")
                .setView(et)
                .setPositiveButton("OK", null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pass = et.getText() != null ? et.getText().toString() : "";
            if (pass.isEmpty()) {
                Toast.makeText(this, "Password requerida", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean ok = PasswordHash.verify(pass.toCharArray(), owner.password);
            if (!ok) {
                Toast.makeText(this, "Password incorrecta", Toast.LENGTH_SHORT).show();
                return; // mantiene el diálogo abierto
            }

            SessionSecrets.setPassword(pass.toCharArray());
            dlg.dismiss();
            cb.onOk(pass.toCharArray());
        });
    }
}