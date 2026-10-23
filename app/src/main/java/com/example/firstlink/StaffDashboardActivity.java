package com.example.firstlink;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.Event;
import com.google.android.material.button.MaterialButton;
import com.example.firstlink.util.GlassBlur;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class StaffDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.staff_dashboard);
        findViewById(R.id.staff_settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));

        Event event = FirstLinkRepository.get(this).getEvent();
        TextView eventNameView = findViewById(R.id.staff_dashboard_event_name);
        if (event != null) {
            eventNameView.setText(event.getName());
        }

        MaterialButton reportEmergencyButton = findViewById(R.id.staff_dashboard_report_emergency_button);
        MaterialButton viewEmergenciesButton = findViewById(R.id.staff_dashboard_show_emergencies_button);
        MaterialButton requestHelpButton = findViewById(R.id.staff_dashboard_request_help_button);
        MaterialButton viewHelpButton = findViewById(R.id.staff_dashboard_show_help_button);
        MaterialButton endEventButton = findViewById(R.id.staff_dashboard_end_event_button);

        reportEmergencyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(StaffDashboardActivity.this, AttendeeEmergencyActivity.class));
            }
        });
        viewEmergenciesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(StaffDashboardActivity.this, StaffViewEmergenciesActivity.class));
            }
        });
        requestHelpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(StaffDashboardActivity.this, AttendeeHelpActivity.class));
            }
        });
        viewHelpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(StaffDashboardActivity.this, StaffViewHelpActivity.class));
            }
        });
        // ReadyMap is organizer planning; staff and attendees never see it.
        findViewById(R.id.staff_dashboard_ready_map_section).setVisibility(
                FirstLinkRepository.get(this).isOrganizer() ? View.VISIBLE : View.GONE);
        findViewById(R.id.staff_dashboard_ready_map_button).setOnClickListener(
                v -> startActivity(new Intent(this, ReadyMapActivity.class)));
        endEventButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmEndEvent();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirstLinkRepository.get(this).setOnChangeListener(() -> Dashboards.refresh(this));
        Dashboards.refresh(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        FirstLinkRepository.get(this).setOnChangeListener(null);
    }

    private void confirmEndEvent() {
        // Danger overlay: the affirmative action here is irreversible, so its pill is red.
        GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_FirstLink_Dialog_Danger)
                .setTitle("End Event")
                .setMessage("This ends the event for everyone: join codes stop working and the "
                        + "event closes for everyone and join codes stop working. This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("End Event", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Sets active=false on the event row, which signs out all connected
                        // users via their realtime listeners.
                        FirstLinkRepository.get(StaffDashboardActivity.this).endEvent(() -> {
                            Toast.makeText(StaffDashboardActivity.this,
                                    "Event ended", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(StaffDashboardActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        }, () -> Toast.makeText(StaffDashboardActivity.this,
                                "Could not end the event. Check your connection and try again.",
                                Toast.LENGTH_LONG).show());
                    }
                })
                .show());
    }
}
