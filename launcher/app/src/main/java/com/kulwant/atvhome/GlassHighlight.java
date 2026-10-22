package com.kulwant.atvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The single glass focus highlight. Drawn directly so moving it never triggers a layout pass. */
final class GlassHighlight extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float radius;

    GlassHighlight(Context c) {
        super(c);
        fill.setColor(0x2EFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(c, 1.5f));
        stroke.setColor(0x70FFFFFF);
    }

    void set(RectF r, float radius) {
        rect.set(r);
        this.radius = radius;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (rect.isEmpty()) return;
        c.drawRoundRect(rect, radius, radius, fill);
        float h = stroke.getStrokeWidth() / 2f;
        c.drawRoundRect(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h, radius, radius, stroke);
    }
}
