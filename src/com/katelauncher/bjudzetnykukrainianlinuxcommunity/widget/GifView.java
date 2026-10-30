package com.gnome.launcher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;

/** Простий програвач анімованого GIF; анімація ставиться на паузу через setRunning(false). */
public final class GifView extends View {
    private Movie movie;
    private long start;
    private boolean running = true;

    public GifView(Context c, File file) {
        super(c);
        try (FileInputStream in = new FileInputStream(file)) {
            movie = Movie.decodeStream(in);
        } catch (Exception ignored) {}
    }

    public void setRunning(boolean r) {
        running = r;
        if (r) { start = 0; invalidate(); }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (movie == null || movie.duration() <= 0) return;
        long now = SystemClock.uptimeMillis();
        if (start == 0) start = now;
        int dur = movie.duration();
        int t = running ? (int) ((now - start) % dur) : 0;
        movie.setTime(t);
        float sx = getWidth() / (float) movie.width();
        float sy = getHeight() / (float) movie.height();
        float s = Math.min(sx, sy);
        if (s <= 0 || Float.isInfinite(s)) s = 1f;
        c.save();
        c.translate((getWidth() - movie.width() * s) / 2f, (getHeight() - movie.height() * s) / 2f);
        c.scale(s, s);
        movie.draw(c, 0, 0);
        c.restore();
        if (running) postInvalidateOnAnimation();
    }
}
