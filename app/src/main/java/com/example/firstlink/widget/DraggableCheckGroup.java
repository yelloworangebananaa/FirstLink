package com.example.firstlink.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import java.util.HashSet;
import java.util.Set;

/** Vertical multi-select container with click-or-drag checkbox selection. */
public class DraggableCheckGroup extends LinearLayout {
    private final Set<Integer> visited = new HashSet<>();
    private float density;
    private boolean tracking;

    public DraggableCheckGroup(Context context) {
        this(context, null);
    }

    public DraggableCheckGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int index = checkboxAt(event.getY());
            if (index >= 0) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return tracking;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                visited.clear();
                tracking = true;
                toggleCheckbox(checkboxAt(event.getY()));
                return true;

            case MotionEvent.ACTION_MOVE:
                toggleCheckbox(checkboxAt(event.getY()));
                return true;

            case MotionEvent.ACTION_UP:
                tracking = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                visited.clear();
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

    private int checkboxAt(float y) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof CheckBox && y >= child.getTop() && y <= child.getBottom()) {
                return i;
            }
        }
        return -1;
    }

    private void toggleCheckbox(int childIndex) {
        if (childIndex < 0 || !visited.add(childIndex)) return;
        View child = getChildAt(childIndex);
        if (child instanceof CheckBox) {
            child.performClick();
        }
    }
}
