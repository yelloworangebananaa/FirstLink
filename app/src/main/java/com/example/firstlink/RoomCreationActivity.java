package com.example.firstlink;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.Event;
import com.example.firstlink.util.CodeGenerator;
import com.google.android.material.button.MaterialButton;

public class RoomCreationActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.room_creation);

        FirstLinkRepository repo = FirstLinkRepository.get(this);
        Event event = repo.getEvent();
        if (event == null) {
            Toast.makeText(this, "No event found. Create an event first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Generate once; reuse on rotation / re-entry so codes stay stable.
        if (event.getAttendeeCode() == null || event.getStaffCode() == null) {
            String attendeeCode = CodeGenerator.generate();
            String staffCode = CodeGenerator.generateDifferentFrom(attendeeCode);
            repo.saveEventCodes(attendeeCode, staffCode);
        }

        final String attendeeCode = event.getAttendeeCode();
        final String staffCode = event.getStaffCode();

        TextView attendeeCodeView = findViewById(R.id.room_code);
        TextView staffCodeView = findViewById(R.id.staff_code);
        attendeeCodeView.setText(attendeeCode);
        staffCodeView.setText(staffCode);

        MaterialButton copyAttendeeButton = findViewById(R.id.copy_room_code_button);
        MaterialButton copyStaffButton = findViewById(R.id.copy_staff_code_button);
        MaterialButton openCheckinButton = findViewById(R.id.open_checkin_button);

        copyAttendeeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard("Attendee code", attendeeCode);
            }
        });

        copyStaffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard("Staff code", staffCode);
            }
        });

        openCheckinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RoomCreationActivity.this, StaffDashboardActivity.class));
            }
        });
    }

    private void copyToClipboard(String label, String code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, code));
        Toast.makeText(this, label + " copied: " + code, Toast.LENGTH_SHORT).show();
    }
}
