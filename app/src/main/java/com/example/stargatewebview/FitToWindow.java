package com.example.stargatewebview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

/**
 * Applies a native WebView zoom after measuring the document at a 100% baseline.
 * No page CSS, JavaScript, or DOM changes are used for fitting.
 */
public class FitToWindow extends WebView {

    public enum Mode {
        FIT_WIDTH,
        FIT_ENTIRE_PAGE
    }

    private static final String TAG = "FitToWindow";
    private static final long DEBOUNCE_MS = 250L;
    private static final long BASELINE_SETTLE_MS = 100L;
    private static final float MIN_SCALE = 0.25f;
    private static final float MAX_SCALE = 1.0f;
    private static final float SCALE_EPSILON = 0.01f;

    private final Handler fitHandler = new Handler(Looper.getMainLooper());
    private final Runnable beginFitRunnable = this::beginFit;

    private Mode mode = Mode.FIT_WIDTH;
    private boolean fittingSuspended = false;
    private int fitGeneration = 0;

    public FitToWindow(Context context) {
        super(context);
    }

    public void setFitMode(Mode mode) {
        this.mode = mode == null ? Mode.FIT_WIDTH : mode;
        scheduleFit();
    }

    public Mode getFitMode() {
        return mode;
    }

    public void scheduleFit() {
        if (fittingSuspended) return;

        fitGeneration++;
        fitHandler.removeCallbacks(beginFitRunnable);
        fitHandler.postDelayed(beginFitRunnable, DEBOUNCE_MS);
    }

    public void suspendFitting() {
        fittingSuspended = true;
        fitGeneration++;
        fitHandler.removeCallbacksAndMessages(null);
        resetToBaseline();
    }

    public void resumeFitting() {
        fittingSuspended = false;
        scheduleFit();
    }

    public void cancelPendingFit() {
        fitGeneration++;
        fitHandler.removeCallbacksAndMessages(null);
    }

    @SuppressWarnings("deprecation")
    private float currentScale() {
        float scale = getScale();
        return scale > 0f ? scale : MAX_SCALE;
    }

    private void resetToBaseline() {
        float current = currentScale();
        if (Math.abs(current - MAX_SCALE) <= SCALE_EPSILON) return;

        zoomBy(MAX_SCALE / current);
    }

    private void beginFit() {
        if (fittingSuspended || getWidth() <= 0 || getHeight() <= 0) return;

        final int generation = ++fitGeneration;
        resetToBaseline();
        fitHandler.postDelayed(() -> measureAndApply(generation, 0), BASELINE_SETTLE_MS);
    }

    private void measureAndApply(int generation, int baselineRetry) {
        if (generation != fitGeneration || fittingSuspended || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float baselineScale = currentScale();
        if (Math.abs(baselineScale - MAX_SCALE) > SCALE_EPSILON && baselineRetry < 2) {
            resetToBaseline();
            fitHandler.postDelayed(
                    () -> measureAndApply(generation, baselineRetry + 1),
                    BASELINE_SETTLE_MS
            );
            return;
        }

        int viewportWidth = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
        int viewportHeight = Math.max(1, getHeight() - getPaddingTop() - getPaddingBottom());
        int contentWidth = Math.max(1, computeHorizontalScrollRange());
        int contentHeight = Math.max(1, computeVerticalScrollRange());

        float widthScale = (float) viewportWidth / (float) contentWidth;
        float targetScale = widthScale;
        if (mode == Mode.FIT_ENTIRE_PAGE) {
            float heightScale = (float) viewportHeight / (float) contentHeight;
            targetScale = Math.min(widthScale, heightScale);
        }

        targetScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale));
        if (Math.abs(targetScale - MAX_SCALE) > SCALE_EPSILON) {
            zoomBy(targetScale);
        }

        Log.d(TAG, "Applied mode=" + mode
                + ", viewport=" + viewportWidth + "x" + viewportHeight
                + ", content=" + contentWidth + "x" + contentHeight
                + ", scale=" + targetScale);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth || height != oldHeight) {
            scheduleFit();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelPendingFit();
        super.onDetachedFromWindow();
    }
}
