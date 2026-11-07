package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockSide;

/** Режим «поверх усіх програм»: кожна рамка — окреме системне overlay-вікно (потрібен дозвіл «поверх інших програм»). */
final class OverlayHost implements WindowHost {
    private final Context ctx;
    private final WindowManager wm;
    private final Map<WinFrame, WindowManager.LayoutParams> lps = new HashMap<>();
    private ZoneView zv; private WindowManager.LayoutParams zlp;

    OverlayHost(Context c) { ctx = c; wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE); }

    private WindowManager.LayoutParams params(WinFrame f) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(f.w, f.h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = f.x; lp.y = f.y;
        return lp;
    }

    @Override public void add(WinFrame f) {
        if (lps.containsKey(f)) return;
        WindowManager.LayoutParams lp = params(f);
        try { wm.addView(f, lp); lps.put(f, lp); } catch (Exception ignored) {}
    }

    @Override public void remove(WinFrame f) {
        if (lps.remove(f) != null) { try { wm.removeViewImmediate(f); } catch (Exception ignored) {} }
    }

    @Override public void raise(WinFrame f) {
        WindowManager.LayoutParams lp = lps.get(f);
        if (lp == null) return;
        try { wm.removeViewImmediate(f); wm.addView(f, lp); } catch (Exception ignored) {}
    }

    @Override public void layout(WinFrame f) {
        WindowManager.LayoutParams lp = lps.get(f);
        if (lp == null) return;
        lp.x = f.x; lp.y = f.y; lp.width = f.w; lp.height = f.h;
        try { wm.updateViewLayout(f, lp); } catch (Exception ignored) {}
    }

    @Override public Rect bounds() { return wm.getCurrentWindowMetrics().getBounds(); }

    @Override public Rect usable() {
        android.view.WindowMetrics m = wm.getCurrentWindowMetrics();
        android.graphics.Insets in = m.getWindowInsets().getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect b = m.getBounds();
        Rect u = new Rect(b.left + in.left, b.top + in.top, b.right - in.right, b.bottom - in.bottom);
        if (Prefs.getInt(Prefs.DOCK_MODE, 0) == 1 && !Prefs.getBool(Prefs.DOCK_HIDDEN, false)) {
            int dw = Ui.dp(ctx, com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Config.DOCK_WIDTH_DP), side = DockSide.current(ctx);
            if (side == DockSide.LEFT) u.left += dw; else if (side == DockSide.RIGHT) u.right -= dw;
            else if (side == DockSide.TOP) u.top += dw; else u.bottom -= dw;
        }
        return u;
    }

    @Override public int[] origin() { return new int[]{0, 0}; }

    @Override public void showZones(List<Rect> zones, int active) {
        if (zones == null) { if (zv != null) { try { wm.removeViewImmediate(zv); } catch (Exception ignored) {} zv = null; } return; }
        if (zv == null) {
            zv = new ZoneView(ctx);
            Rect b = bounds();
            zlp = new WindowManager.LayoutParams(b.width(), b.height(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            zlp.gravity = Gravity.TOP | Gravity.LEFT;
            try { wm.addView(zv, zlp); } catch (Exception e) { zv = null; return; }
        }
        zv.set(zones, active);
    }

    @Override public void setInputFocus(WinFrame f, boolean focus) {
        WindowManager.LayoutParams lp = lps.get(f);
        if (lp == null) return;
        int flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        boolean has = (lp.flags & flag) == 0;
        if (has == focus) return;
        lp.flags = focus ? lp.flags & ~flag : lp.flags | flag;
        try { wm.updateViewLayout(f, lp); } catch (Exception ignored) {}
    }

    @Override public void destroy() {
        for (WinFrame f : new java.util.ArrayList<>(lps.keySet())) remove(f);
        showZones(null, -1);
    }
}
