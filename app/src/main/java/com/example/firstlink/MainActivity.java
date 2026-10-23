package com.example.firstlink;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.firstlink.data.FirstLinkRepository;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    Button join_event_main_page;
    Button create_event_main_page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        findViewById(R.id.home_settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        join_event_main_page = findViewById(R.id.join_btn);
        create_event_main_page = findViewById(R.id.create_btn);

        requestNotificationPermissionIfNeeded();

        // Sign in early so the first backend call doesn't have to wait.
        FirstLinkRepository.get(this).ensureSignedIn(error -> {
            if (error == null) return;
            Log.e("FirstLink", "sign-in failed: " + error);
            Toast.makeText(this, "Cannot reach FirstLink servers.",
                    Toast.LENGTH_LONG).show();
        });

        // Reopening the app mid-event goes straight back to that event, like the iOS app.
        // An ended event is caught by the dashboard, which clears the session and comes back.
        if (savedInstanceState == null && FirstLinkRepository.get(this).getCurrentUser() != null) {
            Dashboards.returnTo(this);
            return;
        }

        join_event_main_page.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AttendeeJoinEventActivity.class);
                startActivity(intent);
            }
        });

        create_event_main_page.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CreatorActivity.class);
                startActivity(intent);
            }
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }
}
