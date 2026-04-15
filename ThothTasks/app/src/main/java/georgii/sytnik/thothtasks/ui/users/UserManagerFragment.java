package georgii.sytnik.thothtasks.ui.users;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.adapter.UserAdapter;
import georgii.sytnik.thothtasks.data.enumtype.UserType;
import georgii.sytnik.thothtasks.data.model.AppUser;
import georgii.sytnik.thothtasks.data.repository.UserRepository;
import georgii.sytnik.thothtasks.databinding.FragmentUserManagerBinding;

public class UserManagerFragment extends Fragment {

    private FragmentUserManagerBinding binding;
    private UserRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserManagerBinding.inflate(inflater, container, false);
        repository = new UserRepository(requireContext());

        binding.recyclerUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.btnCreateLocal.setOnClickListener(v -> showCreateLocalDialog());

        loadUsers();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUsers();
    }

    private void loadUsers() {
        List<AppUser> users = repository.getLocalAndNormalUsers();
        binding.recyclerUsers.setAdapter(new UserAdapter(users));
        binding.emptyView.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCreateLocalDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_local_user, null, false);

        EditText etName = dialogView.findViewById(R.id.etLocalName);
        EditText etPassword = dialogView.findViewById(R.id.etLocalPassword);
        EditText etPort = dialogView.findViewById(R.id.etLocalPort);
        CheckBox cbPasswordRequired = dialogView.findViewById(R.id.cbPasswordRequired);
        CheckBox cbConfirmRequired = dialogView.findViewById(R.id.cbConfirmRequired);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_local_user)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String password = etPassword.getText().toString().trim();
                    String portText = etPort.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(requireContext(), R.string.required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer port = null;
                    if (!portText.isEmpty()) {
                        try {
                            port = Integer.parseInt(portText);
                        } catch (NumberFormatException ignored) { }
                    }

                    AppUser user = new AppUser();
                    user.setUserName(name);
                    user.setPassword(password);
                    user.setType(UserType.LOCAL);
                    user.setPort(port);
                    user.setPasswordRequired(cbPasswordRequired.isChecked());
                    user.setConfirmRequired(cbConfirmRequired.isChecked());

                    repository.insert(user);
                    Toast.makeText(requireContext(), R.string.local_user_created, Toast.LENGTH_SHORT).show();
                    loadUsers();
                })
                .show();
    }
}