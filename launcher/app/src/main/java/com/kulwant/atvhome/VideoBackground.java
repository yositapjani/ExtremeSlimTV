package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Rotating aerial video background (Apple TV style). Plays MP4 files from
 * a backgrounds folder on local storage, switching to a random other one
 * each time a clip finishes, with a short fade. If the folder is empty it
 * falls back to the bundled raw resource. Decoding is done by the platform
 * MediaPlayer, i.e. the Amlogic hardware codec: almost no CPU.
 *
 * Resource discipline:
 *  - files are pre-encoded 720p, ~1.3 Mbps, no audio,
 *  - plays ONLY while the launcher window is visible: opening any app
 *    pauses playback instantly,
 *  - on any playback error it hides itself and the animated gradient
 *    underneath (added before it in the hierarchy) takes over.
 */
public class VideoBackground extends TextureView implements TextureView.SurfaceTextureListener {

    private static final long FADE_MS = 400;

    private final List<Uri> sources = new ArrayList<>();
    private final Random random = new Random();
    private final Runnable onPlaying;
    private final Runnable onFailed;

    private MediaPlayer mp;
    private Surface surface;
    private boolean prepared;
    private boolean wantPlay;
    private boolean announced;
    private int current = -1;

    public VideoBackground(Context c, File dir, Uri fallback, Runnable onPlaying, Runnable onFailed) {
        super(c);
        if (dir != null) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp4"));
            if (files != null) {
                Arrays.sort(files);
                for (File f : files) sources.add(Uri.fromFile(f));
            }
        }
        if (sources.isEmpty() && fallback != null) sources.add(fallback);
        this.onPlaying = onPlaying;
        this.onFailed = onFailed;
        setOpaque(true);
        setAlpha(0f);
        setSurfaceTextureListener(this);
    }

    /** Called by HomeActivity on navigation; just re-asserts playback state. */
    public void poke() {
        updatePlayback();
    }

    private void updatePlayback() {
        if (mp == null || !prepared) return;
        try {
            if (wantPlay && !mp.isPlaying()) mp.start();
            else if (!wantPlay && mp.isPlaying()) mp.pause();
        } catch (IllegalStateException ignored) {
        }
    }

    /**
     * Day/night aware pick: files whose name contains "-day" play 06:00-17:59,
     * "-night" files play 18:00-05:59, files without either tag are wildcards.
     * Falls back to the whole set when the current period has nothing else.
     */
    private int pickForNow(int exclude) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean wantDay = hour >= 6 && hour < 18;
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            if (i == exclude) continue;
            String name = sources.get(i).getLastPathSegment();
            String n = name == null ? "" : name.toLowerCase();
            boolean isDay = n.contains("-day");
            boolean isNight = n.contains("-night");
            if (!isDay && !isNight) pool.add(i);
            else if (wantDay == isDay) pool.add(i);
        }
        if (pool.isEmpty()) {
            for (int i = 0; i < sources.size(); i++) if (i != exclude) pool.add(i);
        }
        if (pool.isEmpty()) return exclude < 0 ? 0 : exclude;
        return pool.get(random.nextInt(pool.size()));
    }

    private void switchTo(int idx) {
        current = idx;
        animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (mp == null) return;
            try {
                mp.reset();
                if (surface != null) mp.setSurface(surface);
                mp.setDataSource(getContext(), sources.get(idx));
                mp.setLooping(false);
                mp.setVolume(0f, 0f);
                prepared = false;
                mp.prepareAsync();
            } catch (IOException | IllegalStateException | IllegalArgumentException e) {
                fail();
            }
        }).start();
    }

    private void fail() {
        releasePlayer();
        setVisibility(GONE);
        if (onFailed != null) post(onFailed);
    }

    private void releasePlayer() {
        prepared = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) {
            }
            mp = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        if (sources.isEmpty()) {
            setVisibility(GONE);
            if (onFailed != null) post(onFailed);
            return;
        }
        try {
            surface = new Surface(st);
            mp = new MediaPlayer();
            mp.setSurface(surface);
            mp.setLooping(false);
            mp.setVolume(0f, 0f);
            mp.setOnCompletionListener(p -> {
                // a clip only changes once it has played to the end,
                // and the next one matches the time of day
                if (sources.size() > 1) {
                    switchTo(pickForNow(current));
                } else {
                    try {
                        p.seekTo(0);
                        if (wantPlay) p.start();
                    } catch (IllegalStateException ignored) {
                    }
                }
            });
            mp.setOnPreparedListener(p -> {
                prepared = true;
                updatePlayback();
                animate().alpha(1f).setDuration(FADE_MS).start();
                if (!announced) {
                    announced = true;
                    if (onPlaying != null) post(onPlaying);
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                fail();
                return true;
            });
            current = pickForNow(-1);
            mp.setDataSource(getContext(), sources.get(current));
            mp.prepareAsync();
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            fail();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        wantPlay = visibility == VISIBLE;
        updatePlayback();
    }

    @Override
    protected void onDetachedFromWindow() {
        wantPlay = false;
        releasePlayer();
        super.onDetachedFromWindow();
    }
}
