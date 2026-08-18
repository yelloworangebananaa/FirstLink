package com.example.firstlink.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Real Gaussian blur for the app's glass language.
 *
 * <p>Android has no CSS-style {@code backdrop-filter}: {@link View#setRenderEffect} blurs a
 * view's <em>own</em> content, not what happens to be behind it. So the blur is applied the
 * way the platform actually supports — to the page itself, while a dialog floats above it
 * unblurred. That is also where blur belongs by design: on the layer behind a floating
 * overlay, never on the content layer during normal use.
 *
 * <p>Requires API 31 (Android 12). Below that the call is a no-op and the dialog simply
 * keeps the existing dimmed-scrim look, so nothing regresses on older devices.
 *
 * <p>Usage: wrap the builder's terminal {@code .show()}, which already returns the dialog:
 * <pre>GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)...show());</pre>
 */
public final class GlassBlur {

    /** Enough softening to read clearly as glass without turning the page into mush. */
    private static final float DIALOG_BACKDROP_BLUR_DP = 20f;

    /**
     * Dialogs stack (a confirm can open straight out of another confirm's button), and the
     * outgoing dialog is dismissed *after* the incoming one is shown. Without a depth count
     * the older dialog's dismiss would strip the blur out from under the newer one.
     */
    private static int depth;
    private static WeakReference<View> blurredPage;

    private GlassBlur() {
    }

    /**
     * Blurs the page behind {@code dialog} for as long as it is showing.
     *
     * @return the same dialog, so this can wrap a builder chain in place.
     */
    public static <T extends Dialog> T behindDialog(T dialog) {
        if (dialog == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return dialog;
        }
        Activity activity = activityOf(dialog.getContext());
        if (activity == null || activity.isFinishing()) {
            return dialog;
        }
        final View page = activity.findViewById(android.R.id.content);
        if (page == null) {
            return dialog;
        }

        // A different page than the one we last blurred means the previous activity went
        // away while a dialog was up; that view is gone, so start the count over.
        if (blurredPage == null || blurredPage.get() != page) {
            depth = 0;
            blurredPage = new WeakReference<>(page);
        }
        depth++;

        float radius = DIALOG_BACKDROP_BLUR_DP
                * activity.getResources().getDisplayMetrics().density;
        page.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                release(page);
            }
        });
        return dialog;
    }

    private static void release(View page) {
        if (blurredPage == null || blurredPage.get() != page) {
            // Superseded by a dialog on a newer page; that page owns the blur now.
            return;
        }
        depth--;
        if (depth <= 0) {
            depth = 0;
            blurredPage = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                page.setRenderEffect(null);
            }
        }
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
