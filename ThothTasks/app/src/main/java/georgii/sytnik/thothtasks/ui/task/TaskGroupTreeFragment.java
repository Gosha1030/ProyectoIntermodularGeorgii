package georgii.sytnik.thothtasks.ui.task;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import georgii.sytnik.thothtasks.adapter.TaskGroupTreeAdapter;
import georgii.sytnik.thothtasks.data.model.TaskGroupNode;
import georgii.sytnik.thothtasks.data.repository.TaskGroupRepository;
import georgii.sytnik.thothtasks.databinding.FragmentTaskGroupTreeBinding;
import georgii.sytnik.thothtasks.util.TaskGroupTreeBuilder;

public class TaskGroupTreeFragment extends Fragment {

    private FragmentTaskGroupTreeBinding binding;
    private TaskGroupRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentTaskGroupTreeBinding.inflate(inflater, container, false);
        repository = new TaskGroupRepository(requireContext());

        binding.recyclerGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        loadTree();

        return binding.getRoot();
    }

    private void loadTree() {
        List<TaskGroupNode> roots =
                TaskGroupTreeBuilder.build(repository.getAll());
        binding.recyclerGroups.setAdapter(new TaskGroupTreeAdapter(roots));
    }
}