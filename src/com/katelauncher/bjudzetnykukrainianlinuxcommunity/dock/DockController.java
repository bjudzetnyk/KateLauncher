package com.gnome.launcher.dock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.gnome.launcher.core.Prefs;
import com.gnome.launcher.desktop.DesktopView;
import com.gnome.launcher.drawer.DrawerView;
import com.gnome.launcher.widget.WidgetLayer;

public final class DockController {
    /** Для оверлею поверх програм: вікна дока й ручки додаються/прибираються за станом. */
    public interface Host {
        void onShown(float f);
        void onSettled(boolean hidden);
    }

    private static final float FLING = 800f;

    private final View dock, handle;
    private final DesktopView desktop;   // може бути null (оверлей)
    private final WidgetLayer widgets;   // може бути null
    private final DrawerView drawer;     // може бути null
    private final float size;
    private int side = DockSide.LEFT;
    private float shown = 1f;
    private ValueAnimator anim;
    private Host host;

    public DockController(View dock, View handle, DesktopView desktop, WidgetLayer widgets, DrawerView drawer, float size) {
        this.dock = dock;
        this.handle = handle;
        this.desktop = desktop;
        this.widgets = widgets;
        this.drawer = drawer;
        this.size = size;
    }

    public float size() { return size; }
    public float shown() { return shown; }
    public void setHost(Host h) { host = h; }

    public void setSide(int s) {
        side = s;
        setShown(shown);
    }

    public void init(boolean hidden) {
        setShown(hidden ? 0f : 1f);
        settle(hidden);
    }

    private void setShown(float f) {
        shown = Math.max(0f, Math.min(1f, f));
        float off = (1f - shown) * size;
        float tx = 0, ty = 0;
        switch (side) {
            case DockSide.LEFT: tx = -off; break;
            case DockSide.RIGHT: tx = off; break;
            case DockSide.TOP: ty = -off; break;
            default: ty = off;
        }
        dock.setTranslationX(tx);
        dock.setTranslationY(ty);
        dock.setVisibility(shown <= 0f ? View.INVISIBLE : View.VISIBLE);

        float s = shown * size, l = 0, t = 0, r = 0, b = 0;
        switch (side) {
            case DockSide.LEFT: l = s; break;
            case DockSide.RIGHT: r = s; break;
            case DockSide.TOP: t = s; break;
            default: b = s;
        }
        boolean shift = Prefs.getBool(Prefs.DOCK_SHIFT, true);
        if (desktop != null) desktop.setDock(l, t, r, b, shift);
        if (widgets != null) widgets.setInsets(shift ? l : 0, shift ? t : 0, shift ? r : 0, shift ? b : 0);
        if (drawer != null) drawer.setInsets(Math.round(l), Math.round(t), Math.round(r), Math.round(b));
        if (host != null) host.onShown(shown);
    }

    private void settle(boolean hidden) {
        handle.setVisibility(hidden ? View.VISIBLE : View.GONE);
        Prefs.putBool(Prefs.DOCK_HIDDEN, hidden);
        if (host != null) host.onSettled(hidden);
    }

    private void cancelAnim() {
        if (anim != null) { anim.removeAllListeners(); anim.cancel(); anim = null; }
    }

    public void dragTo(float f) {
        cancelAnim();
        setShown(f);
    }

    public void release(float vx) {
        boolean show = vx > FLING ? true : vx < -FLING ? false : shown > 0.5f;
        animateTo(show);
    }

    public void animateTo(final boolean show) {
        cancelAnim();
        boolean animOn = Prefs.getBool(Prefs.ANIM_ENABLED, true);
        if (!animOn) {
            setShown(show ? 1f : 0f);
            settle(!show);
            return;
        }
        anim = ValueAnimator.ofFloat(shown, show ? 1f : 0f);
        anim.setDuration(160);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { setShown((Float) a.getAnimatedValue()); }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { anim = null; settle(!show); }
        });
        anim.start();
    }
}
