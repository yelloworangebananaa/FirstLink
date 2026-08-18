package com.example.firstlink.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Opens the phone dialer pre-filled with 911 after an explicit confirmation.
 * ACTION_DIAL never places the call itself — the user still has to press call —
 * which is also why the app needs no CALL_PHONE permission.
 */
public class Dialer {

    private Dialer() {
    }

    public static void confirmAndDial911(final Activity activity) {
        GlassBlur.behindDialog(new MaterialAlertDialogBuilder(activity)
                .setTitle("Call 911?")
                .setMessage("This opens your phone dialer with 911. Only proceed for a real emergency.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Dialer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:911")));
                    }
                })
                .show());
    }
}
