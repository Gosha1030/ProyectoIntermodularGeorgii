package georgii.sytnik.thothtasks.ui.main;

import android.os.Bundle;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.databinding.ActivityMainBinding;
import georgii.sytnik.thothtasks.ui.external.ExternalUsersFragment;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleFragment;
import georgii.sytnik.thothtasks.ui.settings.SettingsFragment;
import georgii.sytnik.thothtasks.ui.task.TaskManagerFragment;
import georgii.sytnik.thothtasks.ui.users.UserManagerFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.open_menu, R.string.close_menu
        );
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        binding.navView.setNavigationItemSelectedListener(this::onNavigationItemSelected);

        if (savedInstanceState == null) {
            replaceFragment(new ScheduleFragment(), getString(R.string.menu_schedule));
            binding.navView.setCheckedItem(R.id.nav_schedule);
        }
    }

    private boolean onNavigationItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_schedule) {
            replaceFragment(new ScheduleFragment(), getString(R.string.menu_schedule));
        } else if (id == R.id.nav_tasks) {
            replaceFragment(new TaskManagerFragment(), getString(R.string.menu_tasks));
        } else if (id == R.id.nav_users) {
            replaceFragment(new UserManagerFragment(), getString(R.string.menu_users));
        } else if (id == R.id.nav_external) {
            replaceFragment(new ExternalUsersFragment(), getString(R.string.menu_external_users));
        } else if (id == R.id.nav_settings) {
            replaceFragment(new SettingsFragment(), getString(R.string.menu_settings));
        }

        binding.drawerLayout.closeDrawers();
        return true;
    }

    private void replaceFragment(Fragment fragment, String title) {
        getSupportActionBar().setTitle(title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}