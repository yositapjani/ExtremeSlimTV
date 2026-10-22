package com.kulwant.atvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Apple TV style home screen. All D-pad navigation is handled here (no Android focus search), so
 * movement is deterministic: zones TOP (pills) / DOCK / GRID, Up/Down move exactly one row.
 */
public class HomeActivity extends Activity {

    private enum Zone { TOP, DOCK, GRID }

    private static final int COLS = 4;
    private static final int PILLS = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    // layout (px)
    private int W, H, side, bandTop, gridTop, rowH;

    private FrameLayout root, content;
    private TextView greeting, clock, date, wifiText;
    private LinearLayout pillRow;
    private final View[] pills = new View[PILLS];
    private View btPill;
    private BluetoothA2dp a2dpProfile;
    private boolean btReceiverOn;

    // OLED care: micro pixel-shift orbit + idle dim of the whole UI layer (not the video)
    private static final boolean CARE_TEST = false;   // fast timings + 5x orbit for verification
    private int careTick;
    private int orbitIdx;
    private long lastInteractMs;
    private boolean uiDimmed;
    private static final int[][] ORBIT = {{0, 0}, {2, 1}, {4, 0}, {2, -1}, {0, -2}, {-2, -1}, {-4, 0}, {-2, 1}};
    private GlassHighlight highlight;
    private View glow;
    private GradientBackground bg;
    private VideoBackground vbg;
    private GradientDrawable glowBg;
    private RecyclerView dockRv, gridRv;
    private TileAdapter dockAdapter, gridAdapter;
    private InputsMenu inputs;

    private Zone zone = Zone.DOCK;
    private int topIdx, dockIdx, gridIdx;
    private final RectF hl = new RectF();
    private float hlRadius, hlRadiusNow;
    private boolean hlPlaced;
    private ValueAnimator hlAnim, glowAnim;
    private int glowColor = 0;
    private int lastTitleApp = -1;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        zone = Zone.values()[prefs.getInt("zone", Zone.DOCK.ordinal())];
        topIdx = prefs.getInt("top", 0);
        if (topIdx != 1) topIdx = 1;   // Inputs + Settings pills gone: Wi-Fi is the only top stop
        dockIdx = prefs.getInt("dock", 0);
        gridIdx = prefs.getInt("grid", 0);
        if (zone == Zone.TOP) zone = Zone.DOCK;   // come back to apps, not the buttons

        buildUi();
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        IntentFilter time = new IntentFilter();
        time.addAction(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        time.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        time.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(statusReceiver, time);

        IntentFilter pk = new IntentFilter();
        pk.addAction(Intent.ACTION_PACKAGE_ADDED);
        pk.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pk.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pk.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pk.addDataScheme("package");
        registerReceiver(packageReceiver, pk);

        setupBluetooth();
        lastInteractMs = System.currentTimeMillis();
        main.postDelayed(careLoop, CARE_TEST ? 10_000 : 30_000);
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        updateWifi();
        refreshBt();
        noteInteraction();
        if (inputs.isOpen()) inputs.close();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: close menus and return to the dock.
        if (inputs.isOpen()) inputs.close();
        if (zone == Zone.GRID && hasWindowFocus()) {
            zone = Zone.DOCK;
            applyFocus(true);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(statusReceiver);
        unregisterReceiver(packageReceiver);
        if (btReceiverOn) {
            unregisterReceiver(btReceiver);
            btReceiverOn = false;
        }
        if (a2dpProfile != null) {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad != null) ad.closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile);
            a2dpProfile = null;
        }
        main.removeCallbacks(careLoop);
        io.shutdownNow();
        super.onDestroy();
    }

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(a)
                    || WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(a)) updateWifi();
            else updateClock();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };

    private void loadApps() {
        io.execute(() -> {
            AppRepo.Result r = AppRepo.load(getApplicationContext());
            main.post(() -> setApps(r));
        });
    }

    private void setApps(AppRepo.Result r) {
        dockAdapter.setApps(r.dock);
        gridAdapter.setApps(r.grid);
        int rows = (r.grid.size() + COLS - 1) / COLS;
        ViewGroup.LayoutParams glp = gridRv.getLayoutParams();
        glp.height = Math.max(rows, 1) * rowH;
        gridRv.setLayoutParams(glp);
        ViewGroup.LayoutParams clp = content.getLayoutParams();
        clp.height = gridTop + Math.max(rows, 1) * rowH + Ui.dp(this, 40);
        content.setLayoutParams(clp);
        lastTitleApp = -1;
        clampIndices();
        // Wait for the RecyclerViews to lay out before placing the highlight.
        content.post(() -> content.post(() -> applyFocus(hlPlaced)));
    }

    private void clampIndices() {
        int nd = dockAdapter.getItemCount(), ng = gridAdapter.getItemCount();
        dockIdx = Math.max(0, Math.min(dockIdx, nd - 1));
        gridIdx = Math.max(0, Math.min(gridIdx, ng - 1));
        if (zone == Zone.GRID && ng == 0) zone = Zone.DOCK;
        if (zone == Zone.DOCK && nd == 0) zone = ng > 0 ? Zone.GRID : Zone.TOP;
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        W = getResources().getDisplayMetrics().widthPixels;
        H = getResources().getDisplayMetrics().heightPixels;
        side = dp(40);
        // tray lowered a touch more: bottom edge sits 7% of the screen height up
        int bandH = dp(37), bandBottom = Math.round(H * 0.07f);
        bandTop = H - bandBottom - bandH;
        int dockCell = dp(74), dockCardW = dp(66), dockCardH = dp(37);
        int gridCell = dp(226), gridCardW = dp(202), gridCardH = dp(114);
        rowH = gridCardH + dp(42);
        gridTop = H + dp(56);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setClipChildren(false);

        if (getResources().getBoolean(R.bool.use_wallpaper)) {
            int id = getResources().getIdentifier("wallpaper", "drawable", getPackageName());
            if (id != 0) {
                ImageView wp = new ImageView(this);
                wp.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wp.setImageResource(id);
                root.addView(wp, new FrameLayout.LayoutParams(-1, -1));
                View scrim = new View(this);
                scrim.setBackgroundColor(0x73000000);
                root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            }
        } else {
            // animated ambient gradient background (fallback / underlay)
            bg = new GradientBackground(this);
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
            // rotating aerial video background on top of the gradient:
            // MP4s from local storage, bundled clip as fallback
            int vidId = getResources().getIdentifier("aerial", "raw", getPackageName());
            File bgDir = null;
            File ext = getExternalFilesDir(null);
            if (ext != null) bgDir = new File(ext, "backgrounds");
            vbg = new VideoBackground(this, bgDir,
                    vidId != 0 ? Uri.parse("android.resource://" + getPackageName() + "/" + vidId) : null,
                    () -> {
                        // video is up: the gradient under it is pure waste, park it
                        if (bg != null) {
                            bg.setHalted(true);
                            bg.setVisibility(View.GONE);
                        }
                    },
                    () -> {
                        // video failed: bring the gradient back
                        if (bg != null) {
                            bg.setVisibility(View.VISIBLE);
                            bg.setHalted(false);
                        }
                    });
            root.addView(vbg, new FrameLayout.LayoutParams(-1, -1));
        }

        content = new FrameLayout(this);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, H * 2));

        // ambient glow behind the dock title
        glow = new View(this);
        glowBg = new GradientDrawable();
        glowBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        // circle fades out before the view edges; scaleY flattens it into an ellipse
        glowBg.setGradientRadius(dp(290));
        glowBg.setGradientCenter(0.4f, 0.5f);
        glowBg.setColors(new int[]{0, 0});
        glow.setBackground(glowBg);
        glow.setScaleY(0.45f);
        int glowCx = W / 2, glowCy = bandTop + bandH / 2;
        content.addView(glow, at(dp(900), dp(600), glowCx - dp(360), glowCy - dp(300)));
        glow.setVisibility(View.GONE);   // selection glow disabled per user request

        // the single glass highlight (below tiles and pills)
        highlight = new GlassHighlight(this);
        highlight.setAlpha(0f);
        content.addView(highlight, new FrameLayout.LayoutParams(-1, -1));

        // greeting, clock, date
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        greeting = Ui.text(this, 400, 19, 0xB3FFFFFF);
        greeting.setShadowLayer(dp(3), 0, dp(1), 0xA6000000);
        clock = Ui.text(this, 150, 94, 0xFFFFFFFF);
        clock.setLetterSpacing(-0.02f);
        clock.setShadowLayer(dp(5), 0, dp(2), 0xA6000000);
        date = Ui.text(this, 300, 20, 0xB3FFFFFF);
        date.setShadowLayer(dp(3), 0, dp(1), 0xA6000000);
        tl.addView(greeting);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = dp(4);
        clp.leftMargin = -dp(3);
        tl.addView(clock, clp);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = dp(6);
        tl.addView(date, dlp);
        content.addView(tl, at(W / 2, -2, side, dp(34)));

        // pills: Wi-Fi icon (+ Bluetooth indicator beside it when an audio device
        // is connected); Inputs hidden, Settings moved into the dock
        pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pills[0] = pill(R.drawable.ic_input, "Inputs");
        pills[0].setVisibility(View.GONE);
        pills[1] = pill(R.drawable.ic_wifi, null);
        shadowize(pills[1]);
        pills[2] = pill(R.drawable.ic_settings, null);
        pills[2].setVisibility(View.GONE);
        btPill = pill(R.drawable.ic_bluetooth, null);
        shadowize(btPill);
        btPill.setVisibility(View.GONE);
        pillRow.addView(pills[0], new LinearLayout.LayoutParams(-2, dp(36)));
        pillRow.addView(btPill, new LinearLayout.LayoutParams(-2, dp(36)));
        LinearLayout.LayoutParams wifiLp = new LinearLayout.LayoutParams(-2, dp(36));
        wifiLp.leftMargin = dp(10);
        pillRow.addView(pills[1], wifiLp);
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams(-2, dp(36));
        setLp.leftMargin = dp(10);
        pillRow.addView(pills[2], setLp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        plp.topMargin = dp(36);
        plp.rightMargin = side;
        content.addView(pillRow, plp);

        // dock
        dockAdapter = new TileAdapter(dockCell, dockCardW, dockCardH, 0, false);
        dockRv = recycler(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false), dockAdapter);
        content.addView(dockRv, at(dockCell * 5, dockCardH, (W - dockCell * 5) / 2,
                bandTop + (bandH - dockCardH) / 2));

        // grid
        TextView appsHeader = Ui.text(this, 500, 15, 0x80FFFFFF);
        appsHeader.setText("Apps");
        appsHeader.setLetterSpacing(0.04f);
        content.addView(appsHeader, at(-2, -2, side + dp(2), gridTop - dp(30)));
        gridAdapter = new TileAdapter(gridCell, gridCardW, gridCardH, rowH - gridCardH, true);
        gridRv = recycler(new GridLayoutManager(this, COLS), gridAdapter);
        content.addView(gridRv, at(gridCell * COLS, rowH, side - (gridCell - gridCardW) / 2, gridTop));

        inputs = new InputsMenu(this);
        root.addView(inputs.panel);
    }

    private FrameLayout.LayoutParams at(int w, int h, int x, int y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    private RecyclerView recycler(RecyclerView.LayoutManager lm, TileAdapter a) {
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(lm);
        rv.setAdapter(a);
        rv.setItemAnimator(null);
        rv.setClipChildren(false);
        rv.setClipToPadding(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setFocusable(false);
        rv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rv.setNestedScrollingEnabled(false);
        return rv;
    }

    private View pill(int icon, String label) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setBackground(Ui.rounded(0x1FFFFFFF, dp(18), dp(1), 0x26FFFFFF));
        int padH = label == null ? dp(9) : dp(14);
        p.setPadding(padH, 0, padH, 0);
        p.setAlpha(0.8f);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        p.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));
        if (label != null) {
            TextView t = Ui.text(this, 400, 14, 0xFFFFFFFF);
            t.setText(label);
            t.setMaxWidth(dp(170));
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(8);
            p.addView(t, lp);
        }
        return p;
    }

    /** Soft drop shadow under an icon pill: a dark capsule peeking out at the bottom. */
    private void shadowize(View p) {
        GradientDrawable sh = new GradientDrawable();
        sh.setShape(GradientDrawable.RECTANGLE);
        sh.setCornerRadius(dp(18));
        sh.setColor(0x59000000);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{sh, p.getBackground()});
        ld.setLayerInset(0, dp(2), dp(4), dp(2), 0);
        ld.setLayerInset(1, 0, 0, 0, dp(3));
        p.setBackground(ld);
    }

    // ------------------------------------------------------------ bluetooth

    private void setupBluetooth() {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) return;
        ad.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                a2dpProfile = (BluetoothA2dp) proxy;
                refreshBt();
            }

            @Override
            public void onServiceDisconnected(int profile) {
                a2dpProfile = null;
                refreshBt();
            }
        }, BluetoothProfile.A2DP);
        IntentFilter bf = new IntentFilter();
        bf.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        bf.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(btReceiver, bf);
        btReceiverOn = true;
    }

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refreshBt();
        }
    };

    /** Bluetooth pill shows only while an A2DP audio device (speaker/headphones) is connected. */
    private void refreshBt() {
        if (btPill == null) return;
        boolean on = false;
        try {
            if (a2dpProfile != null) on = !a2dpProfile.getConnectedDevices().isEmpty();
        } catch (Exception ignored) {
        }
        btPill.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ OLED care

    /**
     * Burn-in guard for OLED panels: every 4 minutes the whole UI layer steps a few
     * pixels along a small orbit (the video background never moves), and after
     * 3 minutes without a remote press the UI dims to 65% until the next press.
     */
    private final Runnable careLoop = new Runnable() {
        @Override
        public void run() {
            careTick++;
            int shiftEvery = CARE_TEST ? 2 : 8;          // tick 30 s (10 s test) -> shift every 4 min
            if (content != null && careTick % shiftEvery == 0) {
                orbitIdx = (orbitIdx + 1) % ORBIT.length;
                int m = CARE_TEST ? 5 : 1;
                content.animate().translationX(dp(ORBIT[orbitIdx][0]) * m)
                        .translationY(dp(ORBIT[orbitIdx][1]) * m)
                        .setDuration(800).setInterpolator(new DecelerateInterpolator()).start();
            }
            long idleLimit = CARE_TEST ? 20_000 : 180_000;
            if (content != null && !uiDimmed
                    && System.currentTimeMillis() - lastInteractMs > idleLimit) {
                uiDimmed = true;
                content.animate().alpha(0.65f).setDuration(600).start();
            }
            main.postDelayed(this, CARE_TEST ? 10_000 : 30_000);
        }
    };

    private void noteInteraction() {
        lastInteractMs = System.currentTimeMillis();
        if (uiDimmed && content != null) {
            uiDimmed = false;
            content.animate().alpha(1f).setDuration(200).start();
        }
    }

    // ---------------------------------------------------------------- status

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        String part = h >= 5 && h < 12 ? "Good morning" : h >= 12 && h < 17 ? "Good afternoon" : "Good evening";
        String name = getString(R.string.greeting_name).trim();
        greeting.setText(name.isEmpty() ? part : part + ", " + name);
        boolean h24 = DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(h24 ? "H:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
    }

    @SuppressWarnings("deprecation")
    private void updateWifi() {
        String text = "Offline";
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        if (ni != null && ni.isConnected()) {
            if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
                text = "Ethernet";
            } else if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                text = "Wi-Fi";
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
                String ssid = wi == null ? null : unquote(wi.getSSID());
                if ((ssid == null || ssid.equals("<unknown ssid>")) && wi != null) {
                    try {
                        List<WifiConfiguration> cfgs = wm.getConfiguredNetworks();
                        if (cfgs != null) for (WifiConfiguration cfg : cfgs) {
                            if (cfg.networkId == wi.getNetworkId()) ssid = unquote(cfg.SSID);
                        }
                    } catch (SecurityException ignored) {
                    }
                }
                if (ssid != null && !ssid.isEmpty() && !ssid.equals("<unknown ssid>")) text = ssid;
            }
        }
        if (wifiText != null) wifiText.setText(text);
    }

    private static String unquote(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) noteInteraction();
        int k = e.getKeyCode();
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BACK;
        if (!nav) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;

        boolean select = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (inputs.isOpen()) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) inputs.move(-1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) inputs.move(1);
            else if (select && e.getRepeatCount() == 0) {
                Intent i = inputs.selected();
                inputs.close();
                if (i != null) launch(i);
            } else inputs.close();   // back, left, right
            return true;
        }

        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: up(); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: down(); break;
            case KeyEvent.KEYCODE_DPAD_LEFT: left(); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: right(); break;
            case KeyEvent.KEYCODE_BACK:
                if (zone != Zone.DOCK && dockAdapter.getItemCount() > 0) {
                    zone = Zone.DOCK;
                    applyFocus(true);
                }
                break;
            default:
                if (e.getRepeatCount() == 0) activate();
        }
        return true;
    }

    private void up() {
        if (zone == Zone.DOCK) {
            zone = Zone.TOP;
        } else if (zone == Zone.GRID) {
            if (gridIdx >= COLS) gridIdx -= COLS;
            else if (dockAdapter.getItemCount() > 0) {
                zone = Zone.DOCK;
                dockIdx = Math.min(gridIdx, dockAdapter.getItemCount() - 1);
            } else zone = Zone.TOP;
        } else return;
        applyFocus(true);
    }

    private void down() {
        int ng = gridAdapter.getItemCount();
        if (zone == Zone.TOP) {
            if (dockAdapter.getItemCount() > 0) zone = Zone.DOCK;
            else if (ng > 0) zone = Zone.GRID;
            else return;
        } else if (zone == Zone.DOCK) {
            if (ng == 0) return;
            zone = Zone.GRID;
            gridIdx = Math.min(dockIdx, ng - 1);
        } else {
            if (gridIdx + COLS < ng) gridIdx += COLS;
            else if (gridIdx / COLS < (ng - 1) / COLS) gridIdx = ng - 1;
            else return;
        }
        applyFocus(true);
    }

    private void left() {
        if (zone == Zone.TOP && topIdx > 1) topIdx--;
        else if (zone == Zone.DOCK && dockIdx > 0) dockIdx--;
        else if (zone == Zone.GRID && gridIdx % COLS > 0) gridIdx--;
        else return;
        applyFocus(true);
    }

    private void right() {
        if (zone == Zone.TOP && topIdx < 1) topIdx++;
        else if (zone == Zone.DOCK && dockIdx < dockAdapter.getItemCount() - 1) dockIdx++;
        else if (zone == Zone.GRID && gridIdx % COLS < COLS - 1 && gridIdx + 1 < gridAdapter.getItemCount()) gridIdx++;
        else return;
        applyFocus(true);
    }

    private void activate() {
        if (zone == Zone.TOP) {
            if (topIdx == 0) {
                int[] loc = new int[2];
                pills[0].getLocationInWindow(loc);
                inputs.open(loc[0], loc[1] + pills[0].getHeight() + dp(8));
            } else if (topIdx == 1) {
                launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } else {
                launch(new Intent(Settings.ACTION_SETTINGS));
            }
        } else if (zone == Zone.DOCK && dockIdx < dockAdapter.getItemCount()) {
            launch(AppRepo.launchIntent(getPackageManager(), dockAdapter.apps.get(dockIdx)));
        } else if (zone == Zone.GRID && gridIdx < gridAdapter.getItemCount()) {
            launch(AppRepo.launchIntent(getPackageManager(), gridAdapter.apps.get(gridIdx)));
        }
    }

    private void launch(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- focus rendering

    private void applyFocus(boolean animate) {
        if (bg != null) bg.poke();   // keep the ambient background alive while navigating
        if (vbg != null) vbg.poke();
        prefs.edit().putInt("zone", zone.ordinal()).putInt("top", topIdx)
                .putInt("dock", dockIdx).putInt("grid", gridIdx).apply();

        dockAdapter.setFocused(dockRv, zone == Zone.DOCK ? dockIdx : -1);
        gridAdapter.setFocused(gridRv, zone == Zone.GRID ? gridIdx : -1);
        for (int i = 0; i < PILLS; i++) {
            float a = zone == Zone.TOP && i == topIdx ? 1f : 0.8f;
            pills[i].animate().alpha(a).setDuration(150).start();
        }

        // scroll: dock/top at rest, grid rows bring the grid up one row at a time
        float shift = 0;
        if (zone == Zone.GRID) {
            int row = gridIdx / COLS;
            shift = gridTop - dp(78) + Math.max(0, row - 1) * rowH;
        }
        if (animate) content.animate().translationY(-shift).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        else content.setTranslationY(-shift);

        // glow colour follows the dock selection
        if (dockAdapter.getItemCount() > 0 && dockIdx != lastTitleApp) {
            lastTitleApp = dockIdx;
            setGlow(dockAdapter.apps.get(dockIdx).glow, animate);
        }
        glow.setAlpha(0f);   // selection glow disabled per user request

        RectF target = targetRect();
        if (target != null) moveHighlight(target, animate && hlPlaced);
    }

    private RectF targetRect() {
        if (zone == Zone.TOP) {
            View p = pills[topIdx];
            float x = pillRow.getLeft() + p.getLeft(), y = pillRow.getTop() + p.getTop();
            if (p.getWidth() == 0) return null;
            float o = dp(3);
            hlRadius = p.getHeight() / 2f + o;
            return new RectF(x - o, y - o, x + p.getWidth() + o, y + p.getHeight() + o);
        }
        RecyclerView rv = zone == Zone.DOCK ? dockRv : gridRv;
        int pos = zone == Zone.DOCK ? dockIdx : gridIdx;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(pos);
        if (vh == null) return null;
        View card = vh.itemView.findViewById(R.id.card);
        float cx = rv.getLeft() + vh.itemView.getLeft() + card.getLeft() + card.getWidth() / 2f;
        float cy = rv.getTop() + vh.itemView.getTop() + card.getTop() + card.getHeight() / 2f;
        float hw = card.getWidth() * TileAdapter.FOCUS_SCALE / 2f + dp(5);
        float hh = card.getHeight() * TileAdapter.FOCUS_SCALE / 2f + dp(5);
        hlRadius = dp(15);
        return new RectF(cx - hw, cy - hh, cx + hw, cy + hh);
    }

    private void moveHighlight(RectF to, boolean animate) {
        if (hlAnim != null) hlAnim.cancel();
        final RectF from = new RectF(hl);
        final float r0 = hlRadiusNow, r1 = hlRadius;
        if (!animate) {
            hl.set(to);
            placeHighlight(hl, r1);
            highlight.animate().alpha(1f).setDuration(200).start();
            hlPlaced = true;
            return;
        }
        hlAnim = ValueAnimator.ofFloat(0f, 1f);
        hlAnim.setDuration(340);
        hlAnim.setInterpolator(new OvershootInterpolator(1.15f));
        hlAnim.addUpdateListener(va -> {
            float t = (float) va.getAnimatedValue();
            hl.set(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t);
            placeHighlight(hl, r0 + (r1 - r0) * Math.min(1f, t));
        });
        hlAnim.start();
    }

    private void placeHighlight(RectF r, float radius) {
        hlRadiusNow = radius;
        highlight.set(r, radius);
    }

    private void setGlow(int color, boolean animate) {
        if (glowAnim != null) glowAnim.cancel();
        int from = glowColor == 0 ? (color & 0x00FFFFFF) : glowColor;
        glowColor = color;
        if (!animate) {
            paintGlow(color);
            return;
        }
        glowAnim = ValueAnimator.ofArgb(from, color);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(va -> paintGlow((int) va.getAnimatedValue()));
        glowAnim.start();
    }

    private void paintGlow(int c) {
        int rgb = c & 0x00FFFFFF;
        int a = (c >>> 24) * 0x5C / 0xFF;
        glowBg.setColors(new int[]{(a << 24) | rgb, ((a / 3) << 24) | rgb, rgb});
    }
}
