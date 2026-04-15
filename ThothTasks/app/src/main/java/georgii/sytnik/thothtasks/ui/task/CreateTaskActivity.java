package georgii.sytnik.thothtasks.ui.task;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.data.enumtype.TaskType;
import georgii.sytnik.thothtasks.data.model.Task;
import georgii.sytnik.thothtasks.data.repository.TaskRepository;
import georgii.sytnik.thothtasks.databinding.ActivityCreateTaskBinding;

public class CreateTaskActivity extends AppCompatActivity {

    private ActivityCreateTaskBinding binding;
    private TaskRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new TaskRepository(this);

        ArrayAdapter<TaskType> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                TaskType.values()
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spTaskType.setAdapter(typeAdapter);

        binding.spTaskType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                TaskType selected = (TaskType) binding.spTaskType.getSelectedItem();
                binding.tilDateRule.setHelperText(getRuleHint(selected));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        binding.btnCreate.setOnClickListener(v -> saveTask());
    }

    private String getRuleHint(TaskType type) {
        switch (type) {
            case UNIQUE:
                return getString(R.string.rule_unique_hint);
            case DAILY:
                return getString(R.string.rule_daily_hint);
            case WEEKLY:
                return getString(R.string.rule_weekly_hint);
            case MONTHLY:
                return getString(R.string.rule_monthly_hint);
            case YEARLY:
                return getString(R.string.rule_yearly_hint);
            case PERIODIC:
                return getString(R.string.rule_periodic_hint);
            default:
                return "";
        }
    }

    private void saveTask() {
        String taskName = binding.etTaskName.getText().toString().trim();
        String dateRule = binding.etDateRule.getText().toString().trim();

        if (taskName.isEmpty()) {
            binding.etTaskName.setError(getString(R.string.required));
            return;
        }

        if (dateRule.isEmpty()) {
            binding.etDateRule.setError(getString(R.string.required));
            return;
        }

        Task task = new Task();
        task.setTaskName(taskName);
        task.setType((TaskType) binding.spTaskType.getSelectedItem());
        task.setDateRule(dateRule);
        task.setState(binding.switchState.isChecked());
        task.setStartTime(binding.etStartTime.getText().toString().trim().isEmpty() ? null :
                binding.etStartTime.getText().toString().trim());

        task.setTimeM(parseInteger(binding.etTimeM.getText().toString().trim()));
        task.setPeriodTimeM(parseInteger(binding.etPeriodTimeM.getText().toString().trim()));
        task.setWhere(binding.etWhere.getText().toString().trim());
        task.setNotifyWhenHow(binding.etNotifyWhenHow.getText().toString().trim());
        task.setWhenStart(binding.etWhenStart.getText().toString().trim());
        task.setMuted(binding.switchMuted.isChecked());

        repository.insert(task);

        Toast.makeText(this, R.string.task_created, Toast.LENGTH_SHORT).show();
        finish();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}