package com.kulwant.atvhome;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compact glass dropdown listing HDMI ports, AV and Live TV; switches via the TV input framework. */
final class InputsMenu {

    static final class Item {
        String label;
        boolean connected;
        Intent intent;
    }

    final FrameLayout panel;
    private final LinearLayout list;
    private final Context c;
    private final List<Item> items = new ArrayList<>();
    private final List<TextView> rows = new ArrayList<>();
    private int sel;

    InputsMenu(Context c) {
        this.c = c;
        panel = new FrameLayout(c);
        panel.setBackground(Ui.rounded(0xEB1C1C1E, Ui.dp(c, 16), Ui.dp(c, 1), 0x24FFFFFF));
        panel.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        panel.setElevation(Ui.dp(c, 12));
        panel.setVisibility(View.GONE);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        panel.addView(list);
    }

    boolean isOpen() {
        return panel.getVisibility() == View.VISIBLE;
    }

    void open(int leftPx, int topPx) {
        load();
        list.removeAllViews();
        rows.clear();
        for (Item it : items) {
            TextView t = Ui.text(c, 400, 15, it.connected ? 0xFFFFFFFF : 0x80FFFFFF);
            t.setText(it.label);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
            list.addView(t, new LinearLayout.LayoutParams(Ui.dp(c, 188), Ui.dp(c, 38)));
            rows.add(t);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = leftPx;
        lp.topMargin = topPx;
        panel.setLayoutParams(lp);
        sel = 0;
        paint();
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.setPivotY(0);
        panel.setScaleY(0.94f);
        panel.animate().alpha(1f).scaleY(1f).setDuration(160).start();
    }

    void close() {
        panel.animate().alpha(0f).setDuration(110).withEndAction(() -> panel.setVisibility(View.GONE)).start();
    }

    void move(int d) {
        int n = sel + d;
        if (n < 0 || n >= items.size()) return;
        sel = n;
        paint();
    }

    Intent selected() {
        return items.isEmpty() ? null : items.get(sel).intent;
    }

    private void paint() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setBackground(i == sel ? Ui.rounded(0x2EFFFFFF, Ui.dp(c, 11), Ui.dp(c, 1), 0x40FFFFFF) : null);
        }
    }

    private void load() {
        items.clear();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return;
        List<Item> hdmi = new ArrayList<>(), av = new ArrayList<>();
        TvInputInfo tuner = null;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isHidden(c)) continue;
            int type = info.getType();
            if (type == TvInputInfo.TYPE_TUNER) {
                if (tuner == null) tuner = info;
                continue;
            }
            if (!info.isPassthroughInput()) continue;
            Item it = new Item();
            CharSequence custom = info.loadCustomLabel(c);
            it.label = String.valueOf(custom != null ? custom : info.loadLabel(c));
            it.connected = tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
            it.intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (type == TvInputInfo.TYPE_HDMI) hdmi.add(it); else av.add(it);
        }
        Collections.sort(hdmi, (a, b) -> a.label.compareTo(b.label));
        items.addAll(hdmi);
        items.addAll(av);
        Intent live = c.getPackageManager().getLeanbackLaunchIntentForPackage("com.android.tv");
        if (live != null || tuner != null) {
            Item it = new Item();
            it.label = "Live TV";
            it.connected = true;
            it.intent = live != null ? live : new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelsUriForInput(tuner.getId()));
            it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            items.add(it);
        }
    }
}
