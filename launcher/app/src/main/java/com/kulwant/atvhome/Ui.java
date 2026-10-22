package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.widget.TextView;

/** Small helpers: dp, the Outfit variable font at a given weight, and shared drawables. */
final class Ui {
    private static final SparseArray<Typeface> FONTS = new SparseArray<>();

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface font(Context c, int weight) {
        Typeface t = FONTS.get(weight);
        if (t == null) {
            try {
                t = new Typeface.Builder(c.getAssets(), "fonts/Outfit.ttf")
                        .setFontVariationSettings("'wght' " + weight)
                        .build();
            } catch (Exception e) {
                t = Typeface.create("sans-serif-light", Typeface.NORMAL);
            }
            FONTS.put(weight, t);
        }
        return t;
    }

    static TextView text(Context c, int weight, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(font(c, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    static GradientDrawable rounded(int fill, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }
}
