package com.example.firstlink.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

/**
 * One photo per report, carried inside the Firestore document as base64 JPEG.
 *
 * ponytail: camera-intent THUMBNAIL only (~10-20 KB), which is what keeps this
 * free of Firebase Storage, a FileProvider and the CAMERA permission. Swap in
 * Storage + an upload URL if full-resolution photos are ever needed.
 */
public final class Photo {

    public static final int REQUEST_CODE = 4201;

    private Photo() {
    }

    public static void capture(Activity activity) {
        try {
            activity.startActivityForResult(
                    new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No camera available", Toast.LENGTH_SHORT).show();
        }
    }

    /** The bitmap the camera hands back in onActivityResult, as a storable string. */
    public static String encode(Intent data) {
        Bitmap bitmap = data == null || data.getExtras() == null
                ? null : (Bitmap) data.getExtras().get("data");
        if (bitmap == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** Shows the photo, or hides the view when there isn't one. */
    public static void show(ImageView view, String encoded) {
        if (view == null) return;
        if (encoded == null || encoded.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
        view.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        view.setVisibility(View.VISIBLE);
    }
}
