package com.example.firstlink;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.Event;
import com.example.firstlink.model.User;

/** Appearance, whether this phone can get alerts, and which event it is in. */
public class SettingsActivity extends BaseActivity {

    /** Its own file, so leaving an event (which clears the session prefs) keeps it. */
    private static final String PREFS_NAME = "firstlink_settings";
    private static final String KEY_NIGHT_MODE = "night_mode";

    /** Applies the saved choice. Called once at process start, before any screen draws. */
    static void applySavedAppearance(Context context) {
        AppCompatDelegate.setDefaultNightMode(context
                .getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        RadioGroup appearance = findViewById(R.id.settings_appearance_group);
        int mode = AppCompatDelegate.getDefaultNightMode();
        appearance.check(mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.settings_appearance_dark
                : mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.settings_appearance_light
                : R.id.settings_appearance_system);
        appearance.setOnCheckedChangeListener((group, checkedId) -> {
            int chosen = checkedId == R.id.settings_appearance_dark ? AppCompatDelegate.MODE_NIGHT_YES
                    : checkedId == R.id.settings_appearance_light ? AppCompatDelegate.MODE_NIGHT_NO
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (chosen == AppCompatDelegate.getDefaultNightMode()) return;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putInt(KEY_NIGHT_MODE, chosen).apply();
            // Recreates every open screen in the new theme.
            AppCompatDelegate.setDefaultNightMode(chosen);
        });

        findViewById(R.id.settings_open_system_settings).setOnClickListener(v -> {
            Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });

        showEvent();
    }

    /** Re-checked on every return, since the usual fix is a trip to the phone's settings. */
    @Override
    protected void onResume() {
        super.onResume();
        boolean allowed = NotificationManagerCompat.from(this).areNotificationsEnabled();
        TextView status = findViewById(R.id.settings_alerts_status);
        status.setText(allowed ? "On" : "Off");
        status.setTextColor(ContextCompat.getColor(this, allowed ? R.color.success : R.color.warning));
        findViewById(R.id.settings_open_system_settings)
                .setVisibility(allowed ? View.GONE : View.VISIBLE);
    }

    private void showEvent() {
        FirstLinkRepository repo = FirstLinkRepository.get(this);
        Event event = repo.getEvent();
        User user = repo.getCurrentUser();
        if (event == null || user == null) return;
        findViewById(R.id.settings_event_section).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.settings_event_name)).setText("Name: " + event.getName());
        ((TextView) findViewById(R.id.settings_event_venue)).setText("Venue: " + event.getVenueName());
        String role = user.getRole();
        ((TextView) findViewById(R.id.settings_event_role)).setText("You are: "
                + Character.toUpperCase(role.charAt(0)) + role.substring(1));
        findViewById(R.id.settings_event_receiving)
                .setVisibility(user.isQualifiedResponder() ? View.VISIBLE : View.GONE);
    }
}
