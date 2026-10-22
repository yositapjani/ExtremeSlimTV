package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.View;

/**
 * Animated ambient gradient background: four large, soft colour blobs
 * (deep indigo, violet, teal, plum) slowly orbiting and breathing over a
 * near-black base. Blobs are pre-rendered bitmaps, so each frame is just
 * four bitmap blits.
 *
 * Resource discipline:
 *  - renders at ~15fps (motion is slow; more frames buy nothing),
 *  - stops entirely while the window is not visible (another app open),
 *  - freezes after 60s without navigation (poke() from HomeActivity
 *    resets the idle timer on every focus change) and resumes on the
 *    next interaction, continuing from the frozen phase.
 */
public class GradientBackground extends View {

    private static final int BASE = 0xFF050507;
    private static final int BLOB_PX = 384;
    private static final long FRAME_MS = 66;
    private static final long IDLE_FREEZE_MS = 60_000;

    private static final int[] COLORS = {
            0xFF2A3A9E, // deep indigo
            0xFF6A2AA8, // violet
            0xFF0E5F6B, // deep teal
            0xFF83204B, // plum
    };
    // anchor x/y, orbit x/y (fractions of screen), period s, size (x screen w), base alpha
    private static final float[][] BLOBS = {
            {0.12f, 0.05f, 0.10f, 0.08f, 71f, 0.85f, 150f},
            {0.88f, 0.18f, 0.08f, 0.10f, 59f, 0.80f, 130f},
            {0.78f, 0.95f, 0.12f, 0.07f, 83f, 0.90f, 120f},
            {0.15f, 0.90f, 0.09f, 0.09f, 67f, 0.75f, 110f},
    };

    private final Bitmap[] bitmaps = new Bitmap[BLOBS.length];
    private final Paint paint = new Paint();
    private final RectF dst = new RectF();

    private boolean loopActive;
    private boolean windowVisible;
    private boolean halted;
    private long runStartNanos;    // when the current run segment began
    private long tAccumNanos;     // animated time banked from earlier segments
    private long lastDrawMs;
    private long lastPokeMs;

    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!loopActive) return;
            long nowMs = frameTimeNanos / 1_000_000L;
            if (nowMs - lastPokeMs >= IDLE_FREEZE_MS) {
                // freeze: bank the animated time and stop the loop
                tAccumNanos += frameTimeNanos - runStartNanos;
                loopActive = false;
                return;
            }
            if (nowMs - lastDrawMs >= FRAME_MS) {
                lastDrawMs = nowMs;
                invalidate();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public GradientBackground(Context c) {
        super(c);
        paint.setDither(true);
        paint.setFilterBitmap(true);
        for (int i = 0; i < BLOBS.length; i++) bitmaps[i] = makeBlob(COLORS[i]);
        lastPokeMs = System.nanoTime() / 1_000_000L;
    }

    private static Bitmap makeBlob(int color) {
        Bitmap b = Bitmap.createBitmap(BLOB_PX, BLOB_PX, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint();
        float r = BLOB_PX / 2f;
        p.setShader(new RadialGradient(r, r, r,
                new int[]{color, color & 0x00FFFFFF},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(r, r, r, p);
        return b;
    }

    /** Called by HomeActivity on every navigation/focus change. */
    public void poke() {
        lastPokeMs = System.nanoTime() / 1_000_000L;
        if (halted) return;
        // Deliberately not gated on the cached windowVisible flag: after an
        // install-over-running-app the lifecycle callbacks can both be
        // missed, and a key press must always be able to revive the loop.
        if (!loopActive && isAttachedToWindow()) startLoop();
    }

    /**
     * Halted = the video background is covering this view. Stops the
     * animation loop entirely; un-halting resumes it if visible.
     */
    public void setHalted(boolean h) {
        halted = h;
        if (h) stopLoop();
        else if (windowVisible) poke();
    }

    private void startLoop() {
        runStartNanos = System.nanoTime();
        lastDrawMs = 0;
        loopActive = true;
        Choreographer.getInstance().postFrameCallback(tick);
    }

    private void stopLoop() {
        if (loopActive) {
            tAccumNanos += System.nanoTime() - runStartNanos;
            loopActive = false;
        }
        Choreographer.getInstance().removeFrameCallback(tick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE) {
            windowVisible = true;
            poke();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        windowVisible = false;
        stopLoop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        windowVisible = visibility == VISIBLE;
        if (windowVisible) poke();
        else stopLoop();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        canvas.drawColor(BASE);
        long now = System.nanoTime();
        double t = (tAccumNanos + (loopActive ? now - runStartNanos : 0)) / 1e9;
        for (int i = 0; i < BLOBS.length; i++) {
            float[] b = BLOBS[i];
            double ang = 2 * Math.PI * (t / b[4]) + i * 1.7;
            float cx = (float) ((b[0] + b[2] * Math.cos(ang)) * w);
            float cy = (float) ((b[1] + b[3] * Math.sin(ang)) * h);
            float size = (float) (b[5] * w * (1 + 0.08 * Math.sin(t * 0.11 + i * 2.1)));
            int alpha = (int) (b[6] + 38 * Math.sin(t * 0.07 + i * 1.3));
            paint.setAlpha(Math.max(40, Math.min(210, alpha)));
            dst.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            canvas.drawBitmap(bitmaps[i], null, dst, paint);
        }
    }
}
