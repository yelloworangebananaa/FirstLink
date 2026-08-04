package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.example.firstlink.data.FirstLinkRepository;
import com.google.android.material.button.MaterialButton;

public class AttendeeJoinEventActivity extends BaseActivity {

    private EditText codeInput;
    private MaterialButton joinButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendee_join_event);

        codeInput = findViewById(R.id.join_code_edit_text);
        joinButton = findViewById(R.id.join_event_button);

        joinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                join();
            }
        });
    }

    private void join() {
        String code = codeInput.getText().toString().trim();
        if (code.isEmpty()) {
            codeInput.setError("Enter a join code");
            return;
        }

        // The lookup is a server round trip, so lock the button meanwhile.
        setBusy(true);
        FirstLinkRepository.get(this).joinWithCode(code, new FirstLinkRepository.JoinCallback() {
            @Override
            public void onJoined(String role) {
                setBusy(false);
                startActivity(new Intent(AttendeeJoinEventActivity.this,
                        QualificationsActivity.class));
                finish();
            }

            @Override
            public void onInvalidCode() {
                setBusy(false);
                codeInput.setError("Invalid code. Check with your event organizer.");
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                codeInput.setError("Cannot reach FirstLink servers. Check your connection.");
            }
        });
    }

    private void setBusy(boolean busy) {
        joinButton.setEnabled(!busy);
        joinButton.setText(busy ? "Joining..." : "Join Event");
    }
}
