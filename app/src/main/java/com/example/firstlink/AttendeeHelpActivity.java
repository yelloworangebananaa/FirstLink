package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;


import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.User;
import com.example.firstlink.util.NotificationHelper;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** Shared by staff and attendees — the reporter returns to their own dashboard. */
public class AttendeeHelpActivity extends BaseActivity {

    private Spinner locationSpinner;
    private EditText descriptionInput;
    private FirstLinkRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendee_help);

        repo = FirstLinkRepository.get(this);

        locationSpinner = findViewById(R.id.attendee_help_location_spinner);
        descriptionInput = findViewById(R.id.attendee_help_description);
        MaterialButton sendButton = findViewById(R.id.attendee_help_send_button);

        List<String> locations = repo.getLocations();
        locations.add(0, "Select location (optional)");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(adapter);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendHelpRequest();
            }
        });
    }

    private void sendHelpRequest() {
        String description = descriptionInput.getText().toString().trim();
        if (description.isEmpty()) {
            descriptionInput.setError("Describe what you need help with");
            return;
        }
        String location = locationSpinner.getSelectedItemPosition() <= 0
                ? "" : locationSpinner.getSelectedItem().toString();

        repo.addHelpRequest(location, description);

        // Staff devices raise their own notification from the Firestore listener.
        Toast.makeText(this, "Help request sent to staff", Toast.LENGTH_LONG).show();

        User user = repo.getCurrentUser();
        Class<?> target = (user != null && user.isStaffLevel())
                ? StaffDashboardActivity.class
                : AttendeeMainDashboardActivity.class;
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
