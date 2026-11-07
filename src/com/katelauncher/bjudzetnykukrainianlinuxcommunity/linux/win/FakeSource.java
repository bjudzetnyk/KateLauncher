package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Тестове джерело з фальшивими вікнами: для розробки менеджера вікон без композитора й Termux.
 * Вікно малює анімований градієнт, лічильник кадрів, останній натиснутий символ і позицію вказівника.
 * Запуск: програма Linux із командою «@fake» (див. LinuxLauncher). Таймер працює лише поки є вікна.
 */
final class FakeSource implements WindowSource {
    private final Context ctx;
    private final Handler hd = new Handler(Looper.getMainLooper());
    private Listener l;
    private final List<FakeWindow> wins = new ArrayList<>();
    private int fps = 30, nextId = 1_000_000, outW = 800, outH = 600;
    private FakeWindow focus;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            boolean any = false;
            for (FakeWindow w : new ArrayList<>(wins)) if (w.visible) { w.frame++; w.render(null); any = true; }
            if (any) hd.postDelayed(this, 1000 / Math.max(1, fps));
        }
    };

    FakeSource(Context c) { ctx = c; }

    @Override public void setListener(Listener l) { this.l = l; }
    @Override public void setOutput(int w, int hh, int scale) { outW = w; outH = hh; }
    @Override public void setFps(int f) { fps = f; }
    @Override public void setFocus(LinuxWindow w) { focus = (FakeWindow) w; for (FakeWindow x : wins) x.render(null); }
    @Override public void setGroup(int g) {}
    @Override public void setClipboard(String t) { for (FakeWindow w : wins) w.note = "clip: " + t; }
    @Override public void stop() { for (FakeWindow w : new ArrayList<>(wins)) w.requestClose(); }

    void spawn(String appKey, String name) {
        FakeWindow w = new FakeWindow(nextId++, appKey, name + " #" + (wins.size() + 1));
        w.w = Math.min(640, Math.max(240, outW * 7 / 10)); w.h = Math.min(420, Math.max(180, outH * 7 / 10));
        w.alloc();
        wins.add(w);
        w.render(null);
        if (l != null) l.onWindowAdded(w);
        hd.removeCallbacks(tick); hd.post(tick);
    }

    private void remove(FakeWindow w) {
        wins.remove(w);
        if (w.buffer != null) { w.buffer.recycle(); w.buffer = null; }
        if (l != null) l.onWindowRemoved(w);
    }

    private final class FakeWindow implements LinuxWindow {
        final int id; final String key; String title;
        int w, h, frame, state; boolean visible = true; LinuxBuffer buffer; Bitmap bmp; String note = "";
        float px = -1, py = -1;

        FakeWindow(int id, String key, String title) { this.id = id; this.key = key; this.title = title; }

        void alloc() {
            if (buffer != null) buffer.recycle();
            if (bmp != null) bmp.recycle();
            buffer = new LinuxBuffer(w, h);
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }

        void render(Rect r) {
            if (bmp == null) return;
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float t = (frame % 360);
            c.drawColor(Color.HSVToColor(new float[]{t, 0.35f, focus == this ? 0.45f : 0.3f}));
            p.setColor(0xFFFFFFFF); p.setTextSize(18);
            c.drawText(title + "  " + w + "x" + h + "  frame " + frame, 12, 28, p);
            c.drawText(note, 12, 54, p);
            p.setColor(focus == this ? 0xFF7CFC00 : 0xFF888888);
            c.drawText(focus == this ? "FOCUS (keys go here)" : "no focus", 12, 80, p);
            if (px >= 0) { p.setColor(0xFFFF4040); c.drawCircle(px, py, 10, p); }
            p.setColor(0x88FFFFFF);
            c.drawRect(w / 2f - 40 + (float) Math.sin(frame / 10.0) * 40, h - 40, w / 2f + 40 + (float) Math.sin(frame / 10.0) * 40, h - 20, p);
            Rect full = new Rect(0, 0, w, h);
            buffer.drawFrom(bmp, full);
            if (l != null) l.onWindowDamage(this, full);
        }

        @Override public int id() { return id; }
        @Override public String appKey() { return key; }
        @Override public String title() { return title; }
        @Override public int width() { return w; }
        @Override public int height() { return h; }
        @Override public int scale() { return 1; }
        @Override public LinuxBuffer buffer() { return buffer; }
        @Override public int state() { return state; }
        @Override public void setState(int s) { state = s; }
        @Override public boolean isPopup() { return false; }
        @Override public boolean isForeignPopup() { return false; }
        @Override public LinuxWindow parent() { return null; }
        @Override public int popupX() { return 0; }
        @Override public int popupY() { return 0; }
        @Override public boolean serverDecorated() { return true; }
        @Override public int minWidth() { return 160; }
        @Override public int minHeight() { return 100; }
        @Override public void requestSize(int nw, int nh, boolean max, boolean resizing) {
            nw = Math.max(64, nw); nh = Math.max(64, nh);
            if (nw == w && nh == h) return;
            w = nw; h = nh; alloc(); render(null);
            if (l != null) l.onWindowChanged(this);
        }
        @Override public void requestClose() { remove(this); }
        @Override public void setVisible(boolean v) { visible = v; if (v) { h2().removeCallbacks(tick); h2().post(tick); } }
        @Override public void focus() { setFocus(this); }
        @Override public void dismissPopups() {}
        @Override public void pointerMove(float x, float y) { px = x; py = y; }
        @Override public void pointerButton(int b, boolean down, float x, float y) { note = "button " + Integer.toHexString(b) + (down ? " down" : " up") + " @" + (int) x + "," + (int) y; }
        @Override public void pointerAxis(int axis, float v, int d) { note = "axis " + axis + " " + v; }
        @Override public void pointerLeave() { px = -1; }
        @Override public void key(int code, boolean down) { if (down) note = "key evdev " + code; }
        @Override public void setGroup(int g) { FakeSource.this.setGroup(g); }
        @Override public WindowSource source() { return FakeSource.this; }
        private Handler h2() { return hd; }
    }
}
