package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.PointerIcon;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;

/** Власний курсор лаунчера для вікон Linux: підміняє системний (лише коли під'єднана миша; малює система, без затримки). */
final class Cursors {
    private Cursors() {}
    private static PointerIcon arrow;

    static PointerIcon arrow(Context c) {
        if (arrow != null) return arrow;
        int sz = Math.min(96, Math.max(24, Ui.dp(c, 24)));
        float k = sz / 24f;
        Bitmap b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.scale(k, k);
        Path p = new Path();
        p.moveTo(2, 2); p.lineTo(2, 19); p.lineTo(6.2f, 15); p.lineTo(9.2f, 21.5f); p.lineTo(12, 20.3f); p.lineTo(9, 14); p.lineTo(14.5f, 14); p.close();
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setStyle(Paint.Style.FILL); q.setColor(0xFFFFFFFF); cv.drawPath(p, q);
        q.setStyle(Paint.Style.STROKE); q.setStrokeWidth(1.4f); q.setStrokeJoin(Paint.Join.ROUND); q.setColor(0xFF1B1B1B); cv.drawPath(p, q);
        arrow = PointerIcon.create(b, 2 * k, 2 * k);
        return arrow;
    }
}
