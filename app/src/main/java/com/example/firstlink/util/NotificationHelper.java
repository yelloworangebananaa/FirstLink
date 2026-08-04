package com.example.firstlink.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.firstlink.EmergencyResponderAlertActivity;

/**
 * Local notifications for the single-device demo.
 *
 * Alerting OTHER phones is impossible without a backend: in the Firebase
 * version these notifications are produced by Firebase Cloud Messaging —
 * a Cloud Function pushes to the event's responder/staff topics and this
 * class is only kept for rendering the received message.
 */
public class NotificationHelper {

    private static final String CHANNEL_ID = "firstlink_alerts";
    private static int nextId = 1;

    private NotificationHelper() {
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "FirstLink Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Emergency and help alerts");
            channel.enableVibration(true);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** High-priority alert for qualified responders; tap opens the responder alert screen. */
    public static void showEmergencyAlert(Context context, String emergencyId, String location) {
        if (!canNotify(context)) return;
        ensureChannel(context);

        Intent intent = new Intent(context, EmergencyResponderAlertActivity.class);
        intent.putExtra(EmergencyResponderAlertActivity.EXTRA_EMERGENCY_ID, emergencyId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, emergencyId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("EMERGENCY — responder needed")
                .setContentText("Location: " + location + ". Tap to respond.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(nextId++, builder.build());
    }

    /** Notification for staff about a new non-emergency help request. */
    public static void showHelpNotification(Context context, String description) {
        if (!canNotify(context)) return;
        ensureChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New help request")
                .setContentText(description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(nextId++, builder.build());
    }
}
