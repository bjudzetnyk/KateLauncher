package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;

/** Режим «лише поверх робочого столу»: рамки — діти шару всередині HomeActivity. */
final class LayerHost implements WindowHost {
    private final FrameLayout layer;
    private final LinuxWm.UsableProvider up;
    private final ZoneView zv;

    LayerHost(FrameLayout layer, LinuxWm.UsableProvider up) {
        this.layer = layer; this.up = up;
        zv = new ZoneView(layer.getContext());
        zv.setVisibility(View.GONE);
        layer.addView(zv, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override public void add(WinFrame f) {
        if (f.getParent() == layer) return;
        if (f.getParent() != null) ((ViewGroup) f.getParent()).removeView(f);
        layer.addView(f, layer.getChildCount() - 1, new FrameLayout.LayoutParams(f.w, f.h));
        layout(f);
        zv.bringToFront();
    }

    @Override public void remove(WinFrame f) { if (f.getParent() == layer) layer.removeView(f); }

    @Override public void raise(WinFrame f) {
        if (f.getParent() != layer) return;
        f.bringToFront();
        zv.bringToFront();
    }

    @Override public void layout(WinFrame f) {
        if (f.getParent() != layer) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) f.getLayoutParams();
        if (lp.width != f.w || lp.height != f.h) { lp.width = f.w; lp.height = f.h; f.setLayoutParams(lp); }
        f.setTranslationX(f.x); f.setTranslationY(f.y);
    }

    @Override public Rect bounds() {
        return new Rect(0, 0, Math.max(0, layer.getWidth() - layer.getPaddingLeft() - layer.getPaddingRight()),
                Math.max(0, layer.getHeight() - layer.getPaddingTop() - layer.getPaddingBottom()));
    }

    @Override public Rect usable() { Rect u = up != null ? up.get() : null; return u != null ? u : bounds(); }

    @Override public int[] origin() {
        int[] l = new int[2]; layer.getLocationOnScreen(l);
        return new int[]{l[0] + layer.getPaddingLeft(), l[1] + layer.getPaddingTop()};
    }

    @Override public void showZones(List<Rect> zones, int active) {
        if (zones == null) { zv.setVisibility(View.GONE); zv.set(null, -1); return; }
        zv.offX = 0; zv.offY = 0;
        zv.setVisibility(View.VISIBLE); zv.set(zones, active);
    }

    @Override public void setInputFocus(WinFrame f, boolean focus) {}

    @Override public void destroy() {
        for (int i = layer.getChildCount() - 1; i >= 0; i--) { View v = layer.getChildAt(i); if (v instanceof WinFrame) layer.removeViewAt(i); }
        layer.removeView(zv);
    }
}
