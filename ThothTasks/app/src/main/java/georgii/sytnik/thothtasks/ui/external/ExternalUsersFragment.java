package georgii.sytnik.thothtasks.ui.external;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.adapter.ExternalUserAdapter;
import georgii.sytnik.thothtasks.data.model.ExternalUser;
import georgii.sytnik.thothtasks.data.repository.ExternalUserRepository;
import georgii.sytnik.thothtasks.databinding.FragmentExternalUsersBinding;
import georgii.sytnik.thothtasks.util.SessionManager;

public class ExternalUsersFragment extends Fragment {

    private FragmentExternalUsersBinding binding;
    private ExternalUserRepository repository;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExternalUsersBinding.inflate(inflater, container, false);
        repository = new ExternalUserRepository(requireContext());
        sessionManager = new SessionManager(requireContext());

        binding.recyclerExternalUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.btnAddExternal.setOnClickListener(v -> showAddExternalDialog());

        loadData();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        List<ExternalUser> items = repository.getAll();
        binding.recyclerExternalUsers.setAdapter(new ExternalUserAdapter(items));
        binding.emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddExternalDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_external_user, null, false);

        EditText etName = dialogView.findViewById(R.id.etExternalName);
        EditText etIp = dialogView.findViewById(R.id.etExternalIp);
        EditText etPort = dialogView.findViewById(R.id.etExternalPort);
        EditText etType = dialogView.findViewById(R.id.etExternalType);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_external_source)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.send_request, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String ipText = etIp.getText().toString().trim();
                    String portText = etPort.getText().toString().trim();
                    String type = etType.getText().toString().trim();

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(ipText) || TextUtils.isEmpty(portText)) {
                        Toast.makeText(requireContext(), R.string.required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer ip;
                    Integer port;
                    try {
                        ip = Integer.parseInt(ipText);
                        port = Integer.parseInt(portText);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ExternalUser item = new ExternalUser();
                    item.setUserId(sessionManager.getUserId() > 0 ? sessionManager.getUserId() : 1L);
                    item.setExternalUserName(name);
                    item.setIp(ip);
                    item.setPort(port);
                    item.setType(type.isEmpty() ? "EXTERNAL" : type);

                    repository.insert(item);

                    Toast.makeText(requireContext(),
                            getString(R.string.request_sent_mock),
                            Toast.LENGTH_LONG).show();

                    loadData();
                })
                .show();
    }
}