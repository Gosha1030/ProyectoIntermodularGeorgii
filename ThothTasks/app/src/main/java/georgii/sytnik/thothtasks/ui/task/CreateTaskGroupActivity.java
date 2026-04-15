package georgii.sytnik.thothtasks.ui.task;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.data.db.DatabaseContract.TaskGroupTable;
import georgii.sytnik.thothtasks.data.db.ThothDbHelper;
import georgii.sytnik.thothtasks.databinding.ActivityCreateTaskGroupBinding;

public class CreateTaskGroupActivity extends AppCompatActivity {

    private ActivityCreateTaskGroupBinding binding;
    private ThothDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTaskGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new ThothDbHelper(this);
        binding.btnCreate.setOnClickListener(v -> saveGroup());
    }

    private void saveGroup() {
        String name = binding.etTaskGroupName.getText().toString().trim();
        if (name.isEmpty()) {
            binding.etTaskGroupName.setError(getString(R.string.required));
            return;
        }

        Integer weight = null;
        String weightText = binding.etWeight.getText().toString().trim();
        if (!weightText.isEmpty()) {
            try {
                weight = Integer.parseInt(weightText);
            } catch (NumberFormatException ignored) {}
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(TaskGroupTable.TASK_GROUP_NAME, name);
        cv.put(TaskGroupTable.STATE, binding.switchState.isChecked() ? 1 : 0);
        cv.put(TaskGroupTable.MUTED, binding.switchMuted.isChecked() ? 1 : 0);
        cv.put(TaskGroupTable.WEIGHT, weight == null ? 0 : weight);

        db.insert(TaskGroupTable.TABLE, null, cv);
        Toast.makeText(this, R.string.task_group_created, Toast.LENGTH_SHORT).show();
        finish();
    }
}