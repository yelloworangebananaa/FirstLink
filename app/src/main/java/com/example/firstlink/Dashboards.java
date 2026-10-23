package com.example.firstlink;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.User;
import com.example.firstlink.util.Dialer;
import com.google.android.material.button.MaterialButton;

/**
 * What the staff and attendee dashboards both show, and how any screen gets back
 * to whichever dashboard fits this person's role.
 */
final class Dashboards {

    private Dashboards() {
    }

    static void returnTo(Activity from) {
        User user = FirstLinkRepository.get(from).getCurrentUser();
        Class<?> target = (user != null && user.isStaffLevel())
                ? StaffDashboardActivity.class
                : AttendeeMainDashboardActivity.class;
        Intent intent = new Intent(from, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        from.startActivity(intent);
        from.finish();
    }

    /** Repaints the shared dashboard state. Call on resume and on every server change. */
    static void refresh(Activity dashboard) {
        bailOutIfEventEnded(dashboard);
        showApprovedCallIfAny(dashboard);
        showMyReportStatus(dashboard);
    }

    /** For report screens: true (and already leaving) when the event is over or the session is gone. */
    static boolean bailOutIfEventGone(Activity screen) {
        FirstLinkRepository repo = FirstLinkRepository.get(screen);
        // A null event right after launch only means it hasn't loaded yet — not that it ended.
        boolean ended = repo.getEvent() != null && !repo.getEvent().isActive();
        if (!ended && repo.getCurrentUser() != null) return false;
        repo.clearSession();
        Toast.makeText(screen, "This event has ended.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(screen, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        screen.startActivity(intent);
        return true;
    }

    /** The organizer may have ended the event from another device. */
    private static void bailOutIfEventEnded(Activity dashboard) {
        FirstLinkRepository repo = FirstLinkRepository.get(dashboard);
        if (repo.getEvent() != null && !repo.getEvent().isActive()) {
            repo.clearSession();
            Toast.makeText(dashboard, "This event has ended.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(dashboard, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            dashboard.startActivity(intent);
        }
    }

    /** Recovery path: the organizer approved after the reporter left the report screen. */
    private static void showApprovedCallIfAny(final Activity dashboard) {
        MaterialButton callButton = dashboard.findViewById(R.id.call_911_approved_button);
        if (FirstLinkRepository.get(dashboard).getMyApprovedCall() == null) {
            callButton.setVisibility(View.GONE);
            return;
        }
        callButton.setVisibility(View.VISIBLE);
        callButton.setOnClickListener(v -> Dialer.confirmAndDial911(dashboard));
    }

    /** Who picked up the report this device sent, if anyone has yet. */
    private static void showMyReportStatus(Activity dashboard) {
        String summary = FirstLinkRepository.get(dashboard).getMyReportAcceptedSummary();
        TextView banner = dashboard.findViewById(R.id.my_report_status);
        banner.setVisibility(summary == null ? View.GONE : View.VISIBLE);
        banner.setText(summary);
    }
}
