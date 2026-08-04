package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.User;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class CreatorActivity extends BaseActivity {

    private TextInputEditText eventNameInput;
    private TextInputEditText venueNameInput;
    private TextInputEditText organizerNameInput;
    private SwitchMaterial approvalSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.creator);

        eventNameInput = findViewById(R.id.event_name_submission);
        venueNameInput = findViewById(R.id.venue_name_submission);
        organizerNameInput = findViewById(R.id.organizer_name_submission);
        approvalSwitch = findViewById(R.id.approval_switch);
        Button createButton = findViewById(R.id.create_event_btn);

        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createEvent();
            }
        });
    }

    private void createEvent() {
        String eventName = textOf(eventNameInput);
        String venueName = textOf(venueNameInput);
        String organizerName = textOf(organizerNameInput);

        boolean valid = true;
        if (eventName.isEmpty()) {
            eventNameInput.setError("Event name is required");
            valid = false;
        }
        if (venueName.isEmpty()) {
            venueNameInput.setError("Venue name is required");
            valid = false;
        }
        if (organizerName.isEmpty()) {
            organizerNameInput.setError("Organizer name is required");
            valid = false;
        }
        if (!valid) return;

        FirstLinkRepository repo = FirstLinkRepository.get(this);
        repo.createEvent(eventName, venueName, organizerName, approvalSwitch.isChecked());
        // The event creator is automatically staff (organizer role). They never see the
        // qualifications screen, so publish their name from the form instead.
        repo.createCurrentUser(User.ROLE_ORGANIZER);
        repo.saveProfile(organizerName, new ArrayList<String>(), false);

        startActivity(new Intent(this, AddPlacesAdminActivity.class));
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
