package georgii.sytnik.thothtasks.ui.task;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import georgii.sytnik.thothtasks.adapter.TaskAdapter;
import georgii.sytnik.thothtasks.data.model.Task;
import georgii.sytnik.thothtasks.data.repository.TaskRepository;
import georgii.sytnik.thothtasks.databinding.FragmentTaskManagerBinding;

public class TaskManagerFragment extends Fragment {

    private FragmentTaskManagerBinding binding;
    private TaskRepository repository;
    private TaskAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentTaskManagerBinding.inflate(inflater, container, false);
        repository = new TaskRepository(requireContext());

        binding.recyclerTasks.setLayoutManager(new LinearLayoutManager(requireContext()));

        binding.btnCreateTask.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CreateTaskActivity.class)));

        binding.btnCreateTaskGroup.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CreateTaskGroupActivity.class)));

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.filter(s.toString());
                    updateEmptyState();
                }
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        loadData();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        List<Task> tasks = repository.getAll();
        adapter = new TaskAdapter(tasks);
        binding.recyclerTasks.setAdapter(adapter);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (adapter == null || adapter.getItemCount() == 0) {
            binding.emptyView.setVisibility(View.VISIBLE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
        }
    }
}