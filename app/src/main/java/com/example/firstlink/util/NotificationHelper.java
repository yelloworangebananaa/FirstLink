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
import com.example.firstlink.StaffViewHelpActivity;

/**
 * Local notifications, raised by this phone when its realtime listener sees a change.
 *
 * Waking a phone whose app is not running needs push messages: a server
 * function would push to the event's responders/staff and this class
 * would only render the received message.
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

    /** Notification for staff about a new help request; tap opens the list to accept it. */
    public static void showHelpNotification(Context context, String title, String description) {
        if (!canNotify(context)) return;
        ensureChannel(context);

        Intent intent = new Intent(context, StaffViewHelpActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(nextId++, builder.build());
    }

    /** Sent back to the reporter: someone has picked their report up. */
    public static void showAcceptedNotification(Context context, String who, String noun) {
        if (!canNotify(context)) return;
        ensureChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Help is on the way")
                .setContentText(who + " is working on your " + noun)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(nextId++, builder.build());
    }
}
