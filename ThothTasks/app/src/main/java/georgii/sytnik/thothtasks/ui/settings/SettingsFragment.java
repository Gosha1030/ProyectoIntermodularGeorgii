package georgii.sytnik.thothtasks.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.ui.auth.LoginActivity;
import georgii.sytnik.thothtasks.util.LocaleHelper;
import georgii.sytnik.thothtasks.util.SessionManager;

public class SettingsFragment extends PreferenceFragmentCompat {

    private SessionManager sessionManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        sessionManager = new SessionManager(requireContext());

        ListPreference language = findPreference("pref_language");
        SwitchPreferenceCompat askPassword = findPreference("pref_ask_password");
        Preference backup = findPreference("pref_backup");
        Preference tutorial = findPreference("pref_tutorial_online");
        Preference logout = findPreference("pref_logout");

        if (language != null) {
            language.setValue(LocaleHelper.getCurrentLocale(requireContext()));
            language.setOnPreferenceChangeListener((preference, newValue) -> {
                String lang = String.valueOf(newValue);
                LocaleHelper.setLocale(requireContext(), lang);
                requireActivity().recreate();
                return true;
            });
        }

        if (askPassword != null) {
            askPassword.setChecked(sessionManager.isAskPasswordEnabled());
            askPassword.setOnPreferenceChangeListener((preference, newValue) -> {
                sessionManager.setAskPasswordEnabled((Boolean) newValue);
                return true;
            });
        }

        if (backup != null) {
            backup.setOnPreferenceClickListener(preference -> {
                Toast.makeText(requireContext(), R.string.backup_coming_soon, Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        if (tutorial != null) {
            tutorial.setOnPreferenceClickListener(preference -> {
                Toast.makeText(requireContext(), R.string.tutorial_online_coming_soon, Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        if (logout != null) {
            logout.setOnPreferenceClickListener(preference -> {
                sessionManager.logout();
                startActivity(new Intent(requireContext(), LoginActivity.class));
                requireActivity().finish();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.menu_settings);
    }
}