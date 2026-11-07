package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;

/** Підсвічування зон снапу. У режимі економії — без прозорості (лише контур). */
final class ZoneView extends View {
    private List<Rect> zones;
    private int active = -1;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    int offX, offY;   // зсув початку цього View відносно координат хоста

    ZoneView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2));
        setWillNotDraw(false);
    }

    void set(List<Rect> z, int act) { zones = z; active = act; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        if (zones == null) return;
        boolean saver = LinuxWm.get().saver();
        float r = Ui.dp(getContext(), 10);
        for (int i = 0; i < zones.size(); i++) {
            Rect z = zones.get(i);
            RectF f = new RectF(z.left - offX + 4, z.top - offY + 4, z.right - offX - 4, z.bottom - offY - 4);
            boolean on = i == active || zones.size() == 1;
            if (!saver) { fill.setColor(on ? Theme.accentAlpha(0x66) : 0x22FFFFFF); c.drawRoundRect(f, r, r, fill); }
            line.setColor(on ? Theme.ACCENT : 0x88FFFFFF);
            c.drawRoundRect(f, r, r, line);
        }
    }
}
