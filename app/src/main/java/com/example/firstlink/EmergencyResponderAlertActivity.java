package com.example.firstlink;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.model.User;
import com.example.firstlink.util.Photo;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * The alert a qualified responder receives. Opened from the local
 * notification (or by tapping an Active emergency in the staff list).
 * In the Firebase version this screen is opened by an FCM data message
 * delivered to every subscribed responder device.
 */
public class EmergencyResponderAlertActivity extends BaseActivity {

    public static final String EXTRA_EMERGENCY_ID = "emergency_id";

    private EmergencyRequest emergency;
    private TextView directionsText;
    private TextView respondersList;
    private TextView acceptedByText;
    private MaterialButton acceptButton;
    private MaterialButton declineButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.emergency_responder_alert);

        FirstLinkRepository repo = FirstLinkRepository.get(this);
        String emergencyId = getIntent().getStringExtra(EXTRA_EMERGENCY_ID);
        emergency = emergencyId == null ? null : repo.getEmergencyById(emergencyId);

        if (emergency == null) {
            Toast.makeText(this, "This emergency is no longer available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView locationText = findViewById(R.id.alert_location);
        TextView descriptionText = findViewById(R.id.alert_description);
        TextView statusText = findViewById(R.id.alert_patient_status);
        TextView timeText = findViewById(R.id.alert_time);
        directionsText = findViewById(R.id.alert_directions);
        acceptButton = findViewById(R.id.alert_accept_button);
        declineButton = findViewById(R.id.alert_decline_button);

        locationText.setText(emergency.getLocation());
        String description = emergency.getDescription();
        descriptionText.setText(description == null || description.isEmpty()
                ? "No description provided" : description);
        statusText.setText(emergency.getConsciousness() + " • " + emergency.getBreathing());
        timeText.setText("Reported at " + DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(emergency.getTimestamp())));
        Photo.show((ImageView) findViewById(R.id.alert_photo), emergency.getPhoto());

        respondersList = findViewById(R.id.alert_responders_list);
        acceptedByText = findViewById(R.id.alert_accepted_by);
        showWhoIsInvolved();

        // Someone else accepting does not close this screen — several responders may
        // take the same emergency. Only MY own acceptance hides the accept button.
        if (repo.acceptedByMe(emergency.getAcceptedUids())) {
            showAcceptedState();
        }

        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirstLinkRepository.get(EmergencyResponderAlertActivity.this)
                        .acceptEmergency(emergency.getId());
                Toast.makeText(EmergencyResponderAlertActivity.this,
                        "You are assigned to this emergency", Toast.LENGTH_SHORT).show();
                showWhoIsInvolved();
                showAcceptedState();
            }
        });

        declineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Leaves the emergency Active for another responder.
                finish();
            }
        });
    }

    /**
     * Who the app alerted, and who took it. "Alerted" means eligible to receive the
     * alert — confirming actual delivery to each phone would need a backend receipt.
     */
    private void showWhoIsInvolved() {
        List<User> responders = FirstLinkRepository.get(this).getQualifiedResponders();
        if (responders.isEmpty()) {
            respondersList.setText("No qualified responders have joined this event yet.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (User u : responders) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(u.getSummary());
            }
            respondersList.setText(sb.toString());
        }

        List<String> accepted = emergency.getAcceptedBy();
        acceptedByText.setText(accepted.isEmpty()
                ? "Accepted by: nobody yet"
                : "Accepted by: " + TextUtils.join(", ", accepted));
    }

    private void showAcceptedState() {
        acceptButton.setVisibility(View.GONE);
        declineButton.setText("Close");
        directionsText.setVisibility(View.VISIBLE);
        // Plain-text directions for now — real turn-by-turn needs venue maps.
        directionsText.setText("Go to: " + emergency.getLocation());
    }
}
