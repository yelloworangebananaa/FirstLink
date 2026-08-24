package com.example.firstlink.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/** RadioGroup whose choices can be scrubbed across with a horizontal drag. */
public class DraggableRadioGroup extends RadioGroup {
    private float density;
    private float downX;
    private float downY;
    private boolean dragging;
    private int lastChoice = -1;

    public DraggableRadioGroup(Context context) {
        this(context, null);
    }

    public DraggableRadioGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                lastChoice = choiceAt(downX);
                selectChoice(lastChoice);
                setPressed(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && Math.hypot(dx, dy) > 4f * density) {
                    dragging = true;
                }
                if (dragging) {
                    int choice = choiceAt(event.getX());
                    if (choice != lastChoice) {
                        selectChoice(choice);
                        lastChoice = choice;
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                setPressed(false);
                performClick();
                getParent().requestDisallowInterceptTouchEvent(false);
                dragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
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

    private int choiceAt(float x) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (x >= child.getLeft() && x <= child.getRight()) {
                return i;
            }
        }
        if (getChildCount() == 0 || getWidth() <= 0) return -1;
        float fraction = Math.max(0f, Math.min(0.999f, x / getWidth()));
        return Math.min(getChildCount() - 1, (int) (fraction * getChildCount()));
    }

    private void selectChoice(int index) {
        if (index < 0 || index >= getChildCount()) return;
        View child = getChildAt(index);
        if (child instanceof RadioButton) {
            ((RadioButton) child).setChecked(true);
        }
    }
}
