package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.model.Event;
import com.example.firstlink.util.Dialer;
import com.google.android.material.button.MaterialButton;

public class AttendeeMainDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendee_main_dashboard);

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
        // The organizer may have ended the event from another device.
        FirstLinkRepository.get(this).setOnChangeListener(new Runnable() {
            @Override
            public void run() {
                bailOutIfEventEnded();
                showApprovedCallIfAny();
            }
        });
        bailOutIfEventEnded();
        showApprovedCallIfAny();
    }

    /** Recovery path: the organizer approved after the reporter left the report screen. */
    private void showApprovedCallIfAny() {
        final EmergencyRequest approved = FirstLinkRepository.get(this).getMyApprovedCall();
        MaterialButton callButton = findViewById(R.id.call_911_approved_button);
        if (approved == null) {
            callButton.setVisibility(View.GONE);
            return;
        }
        callButton.setVisibility(View.VISIBLE);
        callButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialer.confirmAndDial911(AttendeeMainDashboardActivity.this);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        FirstLinkRepository.get(this).setOnChangeListener(null);
    }

    private void bailOutIfEventEnded() {
        FirstLinkRepository repo = FirstLinkRepository.get(this);
        if (repo.getEvent() != null && !repo.getEvent().isActive()) {
            repo.clearSession();
            Toast.makeText(this, "This event has ended.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }
}
