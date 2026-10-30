package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;

/** Перемикач у вигляді таблетки, як у GNOME/Yaru. Кліки обробляє рядок, а не сам перемикач. */
final class GSwitch extends View {
    interface Listener { void onChanged(boolean on); }

    private static final int OFF = 0xFF565656;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int w, h;
    private boolean checked;
    private float pos;
    private Listener listener;
    private ValueAnimator anim;

    GSwitch(Context c, boolean on) {
        super(c);
        w = Ui.dp(c, 46);
        h = Ui.dp(c, 26);
        checked = on;
        pos = on ? 1f : 0f;
    }

    void setListener(Listener l) { listener = l; }

    void toggle() {
        checked = !checked;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(pos, checked ? 1f : 0f);
        anim.setDuration(140);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { pos = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
        if (listener != null) listener.onChanged(checked);
    }

    @Override
    protected void onMeasure(int wm, int hm) { setMeasuredDimension(w, h); }

    private static int lerp(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    @Override
    protected void onDraw(Canvas c) {
        p.setColor(lerp(OFF, Theme.ACCENT, pos));
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2f, h / 2f, p);
        float inset = Ui.dp(getContext(), 3);
        float rad = h / 2f - inset;
        float cx = h / 2f + pos * (w - h);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, h / 2f, rad, p);
    }
}
