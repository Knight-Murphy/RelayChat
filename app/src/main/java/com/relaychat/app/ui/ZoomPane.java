package com.relaychat.app.ui;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Shows a single child scaled and centred: pinch to zoom, drag to pan and double tap to fit it back
 * on screen. The table reader uses it so a wide table is read at once instead of being scrolled
 * sideways inside a bubble.
 */
final class ZoomPane extends FrameLayout {
    private static final float MAX_SCALE = 3f;

    private final ScaleGestureDetector zoomDetector;
    private final GestureDetector tapDetector;
    private float scale = 1f;
    private float smallestScale = 0.4f;
    private float panX;
    private float panY;
    private int naturalWidth;
    private int naturalHeight;
    private boolean centred;

    ZoomPane(Context context) {
        super(context);
        zoomDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        zoom(scale * detector.getScaleFactor());
                        return true;
                    }
                });
        tapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent event) {
                fit();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                panX -= dx;
                panY -= dy;
                applyTransform();
                return true;
            }
        });
    }

    /** Scales the child down until it fits, and centres it. */
    void fit() {
        float availableWidth = availableWidth();
        float availableHeight = availableHeight();
        float target = 1f;
        if (naturalWidth > 0 && naturalHeight > 0 && availableWidth > 0 && availableHeight > 0) {
            target = Math.min(1f, Math.min(availableWidth / naturalWidth,
                    availableHeight / naturalHeight));
        }
        target = Math.max(0.05f, target);
        smallestScale = target;
        scale = target;
        panX = 0f;
        panY = 0f;
        centred = true;
        applyTransform();
    }

    private void zoom(float value) {
        scale = Math.max(smallestScale, Math.min(MAX_SCALE, value));
        applyTransform();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        zoomDetector.onTouchEvent(event);
        tapDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int freeWidth = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int freeHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        naturalWidth = 0;
        naturalHeight = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            child.measure(freeWidth, freeHeight);
            naturalWidth = Math.max(naturalWidth, child.getMeasuredWidth());
            naturalHeight = Math.max(naturalHeight, child.getMeasuredHeight());
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).layout(0, 0, naturalWidth, naturalHeight);
        }
        if (!centred || changed) {
            fit();
        }
    }

    private void applyTransform() {
        View child = getChildAt(0);
        if (child == null) {
            return;
        }
        float limitX = Math.max(0f, (naturalWidth * scale - availableWidth()) / 2f);
        float limitY = Math.max(0f, (naturalHeight * scale - availableHeight()) / 2f);
        panX = Math.max(-limitX, Math.min(limitX, panX));
        panY = Math.max(-limitY, Math.min(limitY, panY));
        child.setPivotX(0f);
        child.setPivotY(0f);
        child.setScaleX(scale);
        child.setScaleY(scale);
        child.setTranslationX(getPaddingLeft() + (availableWidth() - naturalWidth * scale) / 2f
                + panX);
        child.setTranslationY(getPaddingTop() + (availableHeight() - naturalHeight * scale) / 2f
                + panY);
        invalidate();
    }

    private float availableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private float availableHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }
}
