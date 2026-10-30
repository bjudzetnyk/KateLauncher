package com.gnome.launcher.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Прості символічні іконки в стилі Yaru/Adwaita (малюються кодом, без ресурсів). */
final class SymbolView extends View {
    static final int BACK = 1, CHEVRON_RIGHT = 2, CHEVRON_DOWN = 3, CHECK = 4,
            PICTURE = 5, APPEARANCE = 6, DOCK = 7, DESKTOP = 8, GRID = 9, GEAR = 10, INFO = 11;

    private final int type;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    SymbolView(Context c, int type, int color) {
        super(c);
        this.type = type;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(1.9f);
        fill.setStyle(Paint.Style.FILL);
        setColor(color);
    }

    void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidate();
    }

    private void poly(Canvas c, float... p) {
        path.reset();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        c.drawPath(path, stroke);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24f * s) / 2f, (getHeight() - 24f * s) / 2f);
        c.scale(s, s);
        switch (type) {
            case BACK: poly(c, 15, 5, 8, 12, 15, 19); break;
            case CHEVRON_RIGHT: poly(c, 9, 5.5f, 15.5f, 12, 9, 18.5f); break;
            case CHEVRON_DOWN: poly(c, 6, 9, 12, 15, 18, 9); break;
            case CHECK: poly(c, 5, 12.5f, 10, 17, 19, 7); break;
            case PICTURE:
                rect.set(3, 5, 21, 19);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                c.drawCircle(8.5f, 10, 1.8f, fill);
                poly(c, 3.8f, 18, 9, 13, 12.5f, 16.5f, 15.5f, 13.5f, 20.2f, 18);
                break;
            case APPEARANCE:
                c.drawCircle(12, 12, 8.5f, stroke);
                rect.set(3.5f, 3.5f, 20.5f, 20.5f);
                c.drawArc(rect, 90, 180, true, fill);
                break;
            case DOCK:
                rect.set(3, 4, 21, 20);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                rect.set(5.5f, 7, 8.5f, 17);
                c.drawRoundRect(rect, 1.2f, 1.2f, fill);
                break;
            case DESKTOP:
                rect.set(3, 4, 21, 16);
                c.drawRoundRect(rect, 2.2f, 2.2f, stroke);
                poly(c, 12, 16, 12, 20);
                poly(c, 8, 20, 16, 20);
                break;
            case GRID:
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) c.drawCircle(6 + i * 6f, 6 + j * 6f, 1.9f, fill);
                break;
            case GEAR:
                c.drawCircle(12, 12, 4.2f, stroke);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    poly(c, 12 + cs * 7.4f, 12 + sn * 7.4f, 12 + cs * 9.8f, 12 + sn * 9.8f);
                }
                c.drawCircle(12, 12, 7.4f, stroke);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, stroke);
                c.drawCircle(12, 7.6f, 1.1f, fill);
                poly(c, 12, 11, 12, 17);
                break;
            default: break;
        }
        c.restore();
    }
}
