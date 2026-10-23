package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.Event;
import com.google.android.material.button.MaterialButton;

public class AttendeeMainDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendee_main_dashboard);
        findViewById(R.id.attendee_settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));

        Event event = FirstLinkRepository.get(this).getEvent();
        TextView eventNameView = findViewById(R.id.attendee_main_dashboard_event_name);
        if (event != null) {
            eventNameView.setText(event.getName());
        }

        MaterialButton reportEmergencyButton =
                findViewById(R.id.attendee_main_dashboard_report_emergency_button);
        MaterialButton requestHelpButton =
                findViewById(R.id.attendee_main_dashboard_request_help_button);

        reportEmergencyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AttendeeMainDashboardActivity.this,
                        AttendeeEmergencyActivity.class));
            }
        });
        requestHelpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AttendeeMainDashboardActivity.this,
                        AttendeeHelpActivity.class));
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
}
