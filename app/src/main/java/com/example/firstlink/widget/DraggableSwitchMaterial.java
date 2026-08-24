package com.example.firstlink.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.google.android.material.switchmaterial.SwitchMaterial;

/** Switch with click and horizontal drag gestures. */
public class DraggableSwitchMaterial extends SwitchMaterial {
    private final int touchSlop;
    private float downX;
    private boolean dragging;
    private boolean downChecked;

    public DraggableSwitchMaterial(Context context) {
        this(context, null);
    }

    public DraggableSwitchMaterial(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downChecked = isChecked();
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                if (!dragging && Math.abs(dx) > touchSlop) dragging = true;
                if (dragging) {
                    if (dx > 0) setChecked(true);
                    if (dx < 0) setChecked(false);
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    toggle();
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                dragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                setChecked(downChecked);
                getParent().requestDisallowInterceptTouchEvent(false);
                dragging = false;
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
