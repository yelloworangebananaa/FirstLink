package com.example.firstlink.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.google.android.material.button.MaterialButton;

/** Material button with click-or-drag activation and a restrained glass lift effect. */
public class DraggableMaterialButton extends MaterialButton {
    private final int touchSlop;
    private float downRawX;
    private float downRawY;
    private boolean dragging;
    private float density;

    public DraggableMaterialButton(Context context) {
        this(context, null);
    }

    public DraggableMaterialButton(Context context, AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialButtonStyle);
    }

    public DraggableMaterialButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                dragging = false;
                setPressed(true);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    setTranslationX(clamp(dx * 0.12f, -14f * density, 14f * density));
                    setTranslationY(clamp(dy * 0.12f, -10f * density, 10f * density));
                    setScaleX(0.985f);
                    setScaleY(0.985f);
                }
                return true;

            case MotionEvent.ACTION_UP:
                setPressed(false);
                resetGestureVisuals();
                performClick();
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;

            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                resetGestureVisuals();
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void resetGestureVisuals() {
        animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(120L)
                .start();
        dragging = false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
