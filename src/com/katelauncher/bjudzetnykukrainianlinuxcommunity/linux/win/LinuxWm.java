package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxPrefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.KeyTable;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.Native;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.WaylandSource;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.ui.MiniMenu;

/**
 * Менеджер вікон Linux-програм. Працює ЛИШЕ з інтерфейсом LinuxWindow (не знає, звідки вікна).
 * Кожне вікно — окремий об'єкт: заголовок, закрити/згорнути/розгорнути, перетягування, зміна розміру,
 * z-порядок, фокус, снап (краї/кути/Super+стрілки), розкладки (каскад/тайлінг/власні).
 * Жодних потоків і таймерів у спокої: усе рухається подіями.
 */
public final class LinuxWm implements WindowSource.Listener {
    public interface UsableProvider { Rect get(); }

    private static LinuxWm INSTANCE;
    public static LinuxWm get() { return INSTANCE; }
    public static synchronized void init(Context c) { if (INSTANCE == null) INSTANCE = new LinuxWm(c.getApplicationContext()); }

    private static final int CUSTOM_ZONE = 8;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<WinFrame> z = new ArrayList<>();
    private final Map<LinuxWindow, WinFrame> frames = new IdentityHashMap<>();
    private final List<WindowSource> sources = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private WindowHost host;
    private LayerHost layerHost;
    private FrameLayout layer; private UsableProvider usableProvider;
    private boolean homeVisible;
    private WinFrame focused, pendingRaise;
    private int group;
    private float zoomCached; private boolean saverCached;
    private ClipboardManager cm; private String lastClip;
    private boolean clipListening;
    // снап
    private int pendingZone; private Rect pendingRect; private boolean zonesArmed;

    private LinuxWm(Context c) {
        ctx = c;
        reloadPrefs();
        WaylandSource.get().setListener(this);
        sources.add(WaylandSource.get());
    }

    // ---------------- Налаштування ----------------

    public void reloadPrefs() {
        zoomCached = LinuxPrefs.zoom(ctx);
        saverCached = LinuxPrefs.saver();
        for (WindowSource s : sources) { s.setFps(LinuxPrefs.fps()); }
        pushOutput();
        for (WinFrame f : z) { f.refreshChrome(); f.invalidate(); f.content.invalidate(); }
        refreshHost();
    }

    float zoom() { return zoomCached; }
    boolean saver() { return saverCached; }
    int group() { return group; }
    private boolean anim() { return Prefs.getBool(Prefs.ANIM_ENABLED, true) && !saverCached; }
    private int dp(float v) { return Ui.dp(ctx, v); }

    private void pushOutput() {
        Rect u = host != null ? host.usable() : null;
        if (u == null || u.isEmpty()) return;
        for (WindowSource s : sources) s.setOutput(Math.round(u.width() / zoomCached), Math.round(u.height() / zoomCached), LinuxPrefs.bufferScale());
    }

    // ---------------- Хости ----------------

    public void attachLayer(FrameLayout l, UsableProvider up) {
        layer = l; usableProvider = up;
        layerHost = new LayerHost(l, up);
        refreshHost();
    }

    public void detachLayer() {
        if (host == layerHost) host = null;
        if (layerHost != null) layerHost.destroy();
        layerHost = null; layer = null;
        for (WinFrame f : z) if (f.getParent() != null) ((android.view.ViewGroup) f.getParent()).removeView(f);
    }

    public void setHomeVisible(boolean v) { homeVisible = v; }

    /** Обирає хост за налаштуванням і переносить на нього всі рамки. */
    public void refreshHost() {
        WindowHost want;
        if (LinuxPrefs.overlayAll() && Settings.canDrawOverlays(ctx)) {
            want = host instanceof OverlayHost ? host : new OverlayHost(ctx);
        } else want = layerHost;
        if (want == host) { pushOutput(); return; }
        if (host != null) { for (WinFrame f : z) host.remove(f); host.destroy(); }
        host = want;
        if (host != null) for (WinFrame f : z) if (!f.hidden) { host.add(f); host.layout(f); }
        pushOutput();
        if (host != null) for (WinFrame f : z) if (f.zone != Snap.NONE) resnap(f);
    }

    public void onScreenChanged() {
        pushOutput();
        for (WinFrame f : z) { if (f.zone != Snap.NONE && f.zone != CUSTOM_ZONE) resnap(f); else if (!f.popup) clamp(f); }
    }

    private Rect usable() { Rect u = host != null ? host.usable() : null; return u != null ? u : new Rect(0, 0, 1000, 1000); }
    Rect bounds() { Rect b = host != null ? host.bounds() : null; return b != null ? b : new Rect(0, 0, 1000, 1000); }

    float[] toHost(float rawX, float rawY) {
        int[] o = host != null ? host.origin() : new int[]{0, 0};
        return new float[]{rawX - o[0], rawY - o[1]};
    }

    // ---------------- Події джерел ----------------

    @Override public void onWindowAdded(LinuxWindow w) {
        if (frames.containsKey(w)) return;
        final WinFrame f = new WinFrame(ctx, this, w);
        frames.put(w, f);
        z.add(f);
        if (w.isPopup()) {
            f.setContentSize(Math.round(w.width() * zoomCached), Math.round(w.height() * zoomCached));
            placePopup(f);
        } else {
            place(f);
            ensureVisibleHome();
        }
        if (host != null && !f.hidden) { host.add(f); host.layout(f); }
        if (!w.isPopup()) { sourceSetup(w); raise(f); focusFrame(f); }
        else raise(f);
        if (anim() && !w.isPopup()) { f.setAlpha(0f); f.setScaleX(0.96f); f.setScaleY(0.96f); f.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(130).start(); }
        watchClipboard(true);
        changed();
    }

    private void sourceSetup(LinuxWindow w) {
        w.source().setFps(LinuxPrefs.fps());
    }

    private void ensureVisibleHome() {
        if (host == layerHost && !homeVisible) {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(ctx.getPackageName());
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    @Override public void onWindowRemoved(LinuxWindow w) {
        WinFrame f = frames.remove(w);
        if (f == null) return;
        z.remove(f);
        if (host != null) host.remove(f);
        if (focused == f) { focused = null; focusTopmost(); }
        if (pendingRaise == f) pendingRaise = null;
        if (frames.isEmpty()) watchClipboard(false);
        changed();
    }

    @Override public void onWindowChanged(LinuxWindow w) {
        WinFrame f = frames.get(w);
        if (f == null) return;
        f.refreshChrome();
        if (f.popup) { f.setContentSize(Math.round(w.width() * zoomCached), Math.round(w.height() * zoomCached)); placePopup(f); }
        else if (!f.sizing && w.state() == LinuxWindow.STATE_NORMAL && f.zone == Snap.NONE) {
            int cw = Math.round(w.width() * zoomCached), ch = Math.round(w.height() * zoomCached);
            if (cw > 0 && ch > 0 && (Math.abs(cw - f.contentW()) > 2 || Math.abs(ch - f.contentH()) > 2) && !cfgInFlight(f)) {
                f.setContentSize(cw, ch); clamp(f); if (host != null) host.layout(f); repositionPopups(f);
            }
        }
        f.invalidate(); f.content.invalidate();
        changed();
    }

    @Override public void onWindowDamage(LinuxWindow w, Rect r) {
        WinFrame f = frames.get(w);
        if (f != null && !f.hidden) f.content.damage(r);
    }

    @Override public void onWindowRequest(LinuxWindow w, int kind, int arg) {
        WinFrame f = frames.get(w);
        if (f == null) return;
        switch (kind) {
        case Native.REQ_MAXIMIZE: case Native.REQ_FULLSCREEN: if (arg == 1) snap(f, Snap.MAX); else restoreFromSnap(f); break;
        case Native.REQ_MINIMIZE: minimize(f); break;
        case Native.REQ_MOVE: case Native.REQ_RESIZE: f.content.beginExternal(kind, arg); break;
        }
    }

    @Override public void onPopupMoved(LinuxWindow w) { WinFrame f = frames.get(w); if (f != null) placePopup(f); }

    @Override public void onClipboard(String text) {
        if (text == null) return;
        lastClip = text;
        try { getCm().setPrimaryClip(ClipData.newPlainText("linux", text)); } catch (Exception ignored) {}
    }

    @Override public void onSourceGone(WindowSource s) { watchClipboard(!frames.isEmpty()); changed(); }

    // ---------------- Буфер обміну Android -> Linux ----------------

    private ClipboardManager getCm() { if (cm == null) cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE); return cm; }

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override public void onPrimaryClipChanged() { pushAndroidClip(); }
    };

    private void watchClipboard(boolean on) {
        if (on == clipListening) return;
        clipListening = on;
        try { if (on) getCm().addPrimaryClipChangedListener(clipListener); else getCm().removePrimaryClipChangedListener(clipListener); } catch (Exception ignored) {}
    }

    private void pushAndroidClip() {
        try {
            ClipData d = getCm().getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence t = d.getItemAt(0).coerceToText(ctx);
            if (t == null) return;
            String s = t.toString();
            if (s.equals(lastClip)) return;
            lastClip = s;
            for (WindowSource src : sources) src.setClipboard(s);
        } catch (Exception ignored) {}
    }

    // ---------------- Розташування ----------------

    private void place(WinFrame f) {
        LinuxWindow w = f.win;
        Rect u = usable();
        f.refreshChrome();
        int cw = Math.round(w.width() * zoomCached), ch = Math.round(w.height() * zoomCached);
        int maxCw = Math.round((u.width() - f.chromeW()) * 0.96f), maxCh = Math.round((u.height() - f.chromeH()) * 0.96f);
        boolean shrunk = false;
        if (cw > maxCw) { cw = maxCw; shrunk = true; }
        if (ch > maxCh) { ch = maxCh; shrunk = true; }
        f.setContentSize(cw, ch);
        int n = 0; for (WinFrame o : z) if (o != f && !o.popup && !o.hidden) n++;
        int step = dp(28);
        f.x = u.left + dp(20) + (n % 8) * step; f.y = u.top + dp(12) + (n % 8) * step;
        clamp(f);
        if (shrunk) w.requestSize(Math.round(cw / zoomCached), Math.round(ch / zoomCached), false, false);
    }

    private void clamp(WinFrame f) {
        Rect u = usable();
        int minVis = dp(80);
        if (f.x + f.w > u.right) f.x = u.right - f.w;
        if (f.y + f.h > u.bottom) f.y = u.bottom - f.h;
        if (f.x < u.left - f.w + minVis) f.x = u.left - f.w + minVis;
        if (f.x < u.left && f.w <= u.width()) f.x = u.left;
        if (f.y < u.top) f.y = u.top;
        if (f.x > u.right - minVis) f.x = u.right - minVis;
    }

    void clampDrag(WinFrame f) {
        Rect u = usable();
        int minVis = dp(80);
        if (f.y < u.top) f.y = u.top;
        if (f.y > u.bottom - dp(40)) f.y = u.bottom - dp(40);
        if (f.x < u.left - f.w + minVis) f.x = u.left - f.w + minVis;
        if (f.x > u.right - minVis) f.x = u.right - minVis;
    }

    private void placePopup(WinFrame p) {
        LinuxWindow par = p.win.parent();
        WinFrame pf = par != null ? frames.get(par) : null;
        if (pf == null) { p.hidden = true; if (host != null) host.remove(p); return; }
        p.x = pf.contentLeft() + Math.round(p.win.popupX() * zoomCached);
        p.y = pf.contentTop() + Math.round(p.win.popupY() * zoomCached);
        Rect b = bounds();
        if (p.x + p.w > b.right) p.x = Math.max(b.left, b.right - p.w);
        if (p.y + p.h > b.bottom) p.y = Math.max(b.top, b.bottom - p.h);
        p.hidden = pf.hidden;
        if (host != null && !p.hidden) { host.add(p); host.layout(p); }
    }

    private void repositionPopups(WinFrame parent) {
        for (WinFrame o : new ArrayList<>(z)) if (o.popup && o.win.parent() == parent.win) placePopup(o);
    }

    private final Map<WinFrame, Runnable> cfgPending = new IdentityHashMap<>();
    private boolean cfgInFlight(WinFrame f) { return cfgPending.containsKey(f); }

    /** Задає межі рамки; живу зміну розміру передає програмі не частіше ~25 разів/с. */
    void setBounds(final WinFrame f, int x, int y, int w, int h, boolean live) {
        f.x = x; f.y = y; f.w = w; f.h = h;
        f.refreshChrome();
        if (host != null) host.layout(f);
        repositionPopups(f);
        if (!live) { cancelCfg(f); sendSize(f, false); return; }
        if (cfgPending.containsKey(f)) return;
        Runnable r = new Runnable() { @Override public void run() { cfgPending.remove(f); sendSize(f, true); } };
        cfgPending.put(f, r);
        main.postDelayed(r, 40);
    }

    private void cancelCfg(WinFrame f) { Runnable r = cfgPending.remove(f); if (r != null) main.removeCallbacks(r); }

    private void sendSize(WinFrame f, boolean resizing) {
        f.win.requestSize(Math.max(1, Math.round(f.contentW() / zoomCached)), Math.max(1, Math.round(f.contentH() / zoomCached)),
                f.win.state() == LinuxWindow.STATE_MAXIMIZED, resizing);
    }

    void moveFrame(WinFrame f) { if (host != null) host.layout(f); repositionPopups(f); }

    void finishResize(WinFrame f) { f.zone = Snap.NONE; f.win.setState(LinuxWindow.STATE_NORMAL); cancelCfg(f); f.refreshChrome(); sendSize(f, false); f.invalidate(); }

    // ---------------- Фокус і z-порядок ----------------

    /** Дотик у рамку: фокус + підняття. */
    void activate(WinFrame f) {
        WinFrame t = f.popup ? parentFrame(f) : f;
        if (t == null) return;
        if (focused != t) focusFrame(t);
        if (z.indexOf(t) != z.size() - 1 - popupCount(t)) {
            if (host instanceof OverlayHost) { pendingRaise = t; z.remove(t); z.add(t); } else raise(t);
        }
    }

    void flushRaise() {
        WinFrame t = pendingRaise; pendingRaise = null;
        if (t != null && host != null && z.contains(t)) raise(t);
    }

    private WinFrame parentFrame(WinFrame p) { LinuxWindow par = p.win.parent(); return par != null ? frames.get(par) : null; }
    private int popupCount(WinFrame t) { int n = 0; for (WinFrame o : z) if (o.popup && o.win.parent() == t.win) n++; return n; }

    private void raise(WinFrame f) {
        z.remove(f); z.add(f);
        if (host != null && !f.hidden) host.raise(f);
        for (WinFrame o : new ArrayList<>(z)) if (o.popup && o.win.parent() == f.win) { z.remove(o); z.add(o); if (host != null && !o.hidden) host.raise(o); }
    }

    private void focusFrame(WinFrame f) {
        if (f == null || f.popup) return;
        if (focused != null && focused != f && host != null) host.setInputFocus(focused, false);
        focused = f;
        for (WinFrame o : z) o.setFocusedLook(o == f);
        f.win.focus();
        if (host != null) host.setInputFocus(f, true);
        f.content.requestFocus();
        pushAndroidClip();
        changed();
    }

    /** Дотик на робочий стіл / поза вікнами: жодне вікно не отримує клавіші. */
    public void clearFocus() {
        if (focused == null) return;
        WinFrame o = focused;
        focused = null;
        for (WinFrame f : z) f.setFocusedLook(false);
        for (WindowSource s : sources) s.setFocus(null);
        if (host != null) host.setInputFocus(o, false);
        hideIme(o);
        o.content.clearFocus();
        changed();
    }

    /** Для HomeActivity.dispatchTouchEvent / ACTION_OUTSIDE: чи потрапив дотик у якусь рамку. */
    public boolean frameContains(float rawX, float rawY) {
        float[] h = toHost(rawX, rawY);
        for (WinFrame f : z) if (!f.hidden && h[0] >= f.x && h[0] < f.x + f.w && h[1] >= f.y && h[1] < f.y + f.h) return true;
        return false;
    }

    public void onTouchOutside(float rawX, float rawY) { if (!frameContains(rawX, rawY)) clearFocus(); }

    private void focusTopmost() {
        for (int i = z.size() - 1; i >= 0; i--) { WinFrame f = z.get(i); if (!f.popup && !f.hidden) { focusFrame(f); return; } }
        for (WindowSource s : sources) s.setFocus(null);
    }

    boolean dismissPopupsFor(LinuxWindow w) {
        boolean any = false;
        boolean grabbing = false;
        for (WinFrame o : z) if (o.popup && o.win.parent() == w) { any = true; if (!o.win.isForeignPopup()) grabbing = true; }
        if (any) w.dismissPopups();
        return grabbing;   // клік «з'їдається» лише коли закрито popup-и Wayland; X11 закриває своє меню сам
    }

    // ---------------- Згортання, розгортання, снап ----------------

    void minimize(WinFrame f) {
        if (f.popup || f.hidden) return;
        f.prevState = f.win.state() == LinuxWindow.STATE_MINIMIZED ? LinuxWindow.STATE_NORMAL : f.win.state();
        f.win.setState(LinuxWindow.STATE_MINIMIZED);
        f.hidden = true;
        if (host != null) host.remove(f);
        for (WinFrame o : z) if (o.popup && o.win.parent() == f.win) { o.hidden = true; if (host != null) host.remove(o); }
        f.win.setVisible(false);
        if (focused == f) { focused = null; focusTopmost(); }
        changed();
    }

    void restore(WinFrame f) {
        if (!f.hidden) return;
        f.hidden = false;
        f.win.setState(f.prevState);
        if (host != null) { host.add(f); host.layout(f); }
        f.win.setVisible(true);
        raise(f);
        focusFrame(f);
        if (anim()) { f.setAlpha(0f); f.animate().alpha(1f).setDuration(110).start(); }
        changed();
    }

    void toggleMaximize(WinFrame f) {
        if (f.popup) return;
        if (f.win.state() == LinuxWindow.STATE_MAXIMIZED) restoreFromSnap(f); else snap(f, Snap.MAX);
    }

    void snap(WinFrame f, int zone) {
        if (f.popup || zone == Snap.NONE) { if (zone == Snap.NONE) restoreFromSnap(f); return; }
        Rect u = usable();
        Rect r = Snap.rect(zone, u);
        if (r == null) return;
        snapRect(f, zone, r);
    }

    private void snapRect(WinFrame f, int zone, Rect r) {
        if (f.zone == Snap.NONE && f.win.state() != LinuxWindow.STATE_MAXIMIZED) f.restore = new Rect(f.x, f.y, f.x + f.w, f.y + f.h);
        f.zone = zone;
        f.win.setState(zone == Snap.MAX ? LinuxWindow.STATE_MAXIMIZED : LinuxWindow.STATE_NORMAL);
        f.refreshChrome();
        setBounds(f, r.left, r.top, r.width(), r.height(), false);
        f.invalidate();
        changed();
    }

    private void resnap(WinFrame f) { Rect r = Snap.rect(f.zone, usable()); if (r != null) setBounds(f, r.left, r.top, r.width(), r.height(), false); }

    void restoreFromSnap(WinFrame f) {
        f.zone = Snap.NONE;
        f.win.setState(LinuxWindow.STATE_NORMAL);
        f.refreshChrome();
        Rect r = f.restore;
        if (r == null) {
            int cw = Math.round(f.win.width() * zoomCached), ch = Math.round(f.win.height() * zoomCached);
            f.setContentSize(cw, ch);
            r = new Rect(f.x, f.y, f.x + f.w, f.y + f.h);
        }
        f.restore = null;
        setBounds(f, r.left, r.top, r.width(), r.height(), false);
        clamp(f); if (host != null) host.layout(f);
        f.invalidate();
        changed();
    }

    // ---------------- Перетягування і підсвітка зон ----------------

    void updateSnapPreview(WinFrame f, float hx, float hy) {
        if (f.popup || host == null) return;
        Rect u = usable();
        Snap.Layout act = Snap.find(Prefs.getString(LinuxPrefs.ACTIVE_LAYOUT, ""));
        pendingZone = Snap.NONE; pendingRect = null;
        if (act != null) {
            if (!zonesArmed && hy <= u.top + dp(64)) zonesArmed = true;
            if (zonesArmed) {
                List<Rect> rs = act.rects(u);
                int idx = -1;
                if (hy > u.top + dp(12)) for (int i = 0; i < rs.size(); i++) if (rs.get(i).contains(Math.round(hx), Math.round(hy))) { idx = i; break; }
                host.showZones(rs, idx);
                if (idx >= 0) pendingRect = rs.get(idx);
                return;
            }
        }
        int zn = Snap.detect(Math.round(hx), Math.round(hy), u, dp(18), dp(72));
        if (zn != Snap.NONE) { List<Rect> l = new ArrayList<>(); l.add(Snap.rect(zn, u)); host.showZones(l, 0); pendingZone = zn; }
        else host.showZones(null, -1);
    }

    void endDrag(WinFrame f, float hx, float hy) {
        if (host != null) host.showZones(null, -1);
        zonesArmed = false;
        if (pendingRect != null) { Rect r = pendingRect; pendingRect = null; snapRect(f, CUSTOM_ZONE, r); }
        else if (pendingZone != Snap.NONE) { int zn = pendingZone; pendingZone = Snap.NONE; snap(f, zn); }
        flushRaise();   // розмір при простому переміщенні не чіпаємо: інакше CSD-вікна (з тінню) росли б при кожному русі
    }

    void cancelDrag(WinFrame f) { if (host != null) host.showZones(null, -1); zonesArmed = false; pendingZone = Snap.NONE; pendingRect = null; }

    // ---------------- Кнопки заголовка ----------------

    void onTitleButton(WinFrame f, int id) {
        switch (id) {
        case WinFrame.B_CLOSE: f.win.requestClose(); break;
        case WinFrame.B_MIN: minimize(f); break;
        case WinFrame.B_MAX: toggleMaximize(f); break;
        case WinFrame.B_LAYOUT: showLayoutMenu(f); break;
        case WinFrame.B_KBD: toggleKeyboard(f); break;
        case WinFrame.B_LANG: toggleGroup(); break;
        }
    }

    private void showLayoutMenu(final WinFrame f) {
        MiniMenu m = new MiniMenu(ctx);
        m.add(I18n.t("lw_cascade"), new Runnable() { @Override public void run() { cascade(); } });
        m.add(I18n.t("lw_tile"), new Runnable() { @Override public void run() { tile(); } });
        for (final Snap.Layout l : Snap.custom()) {
            m.add(I18n.t("lw_zones_prefix") + " " + l.name, new Runnable() { @Override public void run() { Prefs.putString(LinuxPrefs.ACTIVE_LAYOUT, l.id); applyLayout(l); } });
        }
        if (!Prefs.getString(LinuxPrefs.ACTIVE_LAYOUT, "").isEmpty())
            m.add(I18n.t("lw_zones_off"), new Runnable() { @Override public void run() { Prefs.putString(LinuxPrefs.ACTIVE_LAYOUT, ""); } });
        int[] loc = new int[2]; f.getLocationOnScreen(loc);
        m.showAt(f, loc[0] + f.w - dp(250), loc[1] + dp(40));
    }

    private List<WinFrame> visibleToplevels() {
        List<WinFrame> l = new ArrayList<>();
        for (WinFrame f : z) if (!f.popup && !f.hidden) l.add(f);
        return l;
    }

    private void place(WinFrame f, Rect r) {
        f.zone = Snap.NONE; f.restore = null; f.win.setState(LinuxWindow.STATE_NORMAL); f.refreshChrome();
        setBounds(f, r.left, r.top, r.width(), r.height(), false);
    }

    public void cascade() {
        Rect u = usable(); int step = dp(30), i = 0;
        for (WinFrame f : visibleToplevels()) {
            int w = Math.round(u.width() * 0.62f), h = Math.round(u.height() * 0.7f);
            int x = u.left + dp(12) + (i % 8) * step, y = u.top + dp(8) + (i % 8) * step;
            place(f, new Rect(x, y, Math.min(u.right, x + w), Math.min(u.bottom, y + h)));
            raise(f); i++;
        }
        changed();
    }

    public void tile() {
        List<WinFrame> l = visibleToplevels();
        List<Rect> rs = Snap.tile(l.size(), usable());
        for (int i = 0; i < l.size(); i++) place(l.get(i), rs.get(i));
        changed();
    }

    void applyLayout(Snap.Layout lay) {
        List<WinFrame> l = visibleToplevels();
        List<Rect> rs = lay.rects(usable());
        int n = Math.min(l.size(), rs.size());
        for (int i = 0; i < n; i++) { WinFrame f = l.get(l.size() - 1 - i); snapRect(f, CUSTOM_ZONE, rs.get(i)); }
        changed();
    }

    // ---------------- Клавіатура ----------------

    void toggleKeyboard(WinFrame f) {
        f.content.textEditor = !f.content.textEditor;
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (host != null) host.setInputFocus(f, true);
        focusFrame(f);
        f.content.requestFocus();
        imm.restartInput(f.content);
        if (f.content.textEditor) imm.showSoftInput(f.content, 0); else imm.hideSoftInputFromWindow(f.content.getWindowToken(), 0);
    }

    private void hideIme(WinFrame f) {
        f.content.textEditor = false;
        try { ((InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.content.getWindowToken(), 0); } catch (Exception ignored) {}
    }

    void toggleGroup() {
        group = group == 0 ? 1 : 0;
        for (WindowSource s : sources) s.setGroup(group);
        for (WinFrame f : z) f.invalidate();
    }

    private void setGroup(int g) {
        if (group == g) return;
        group = g;
        for (WindowSource s : sources) s.setGroup(g);
        for (WinFrame f : z) f.invalidate();
    }

    /** Апаратна/екранна клавіша -> програмі Linux. true — подію поглинуто. */
    boolean onKey(WinFrame f, KeyEvent e, boolean down) {
        int kc = e.getKeyCode();
        if (kc == KeyEvent.KEYCODE_BACK || kc == KeyEvent.KEYCODE_HOME || kc == KeyEvent.KEYCODE_APP_SWITCH || kc == KeyEvent.KEYCODE_VOLUME_UP || kc == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        // Super+стрілки (як Win+стрілки) та Super+Space — команди менеджера вікон
        if (e.isMetaPressed() && kc != KeyEvent.KEYCODE_META_LEFT && kc != KeyEvent.KEYCODE_META_RIGHT) {
            int dir = kc == KeyEvent.KEYCODE_DPAD_LEFT ? 0 : kc == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : kc == KeyEvent.KEYCODE_DPAD_UP ? 2 : kc == KeyEvent.KEYCODE_DPAD_DOWN ? 3 : -1;
            if (dir >= 0) { if (down && e.getRepeatCount() == 0) superArrow(f, dir); return true; }
            if (kc == KeyEvent.KEYCODE_SPACE) { if (down && e.getRepeatCount() == 0) toggleGroup(); return true; }
        }
        int code = AndroidKeys.evdev(e);
        if (code == 0) return false;
        if (down && e.getRepeatCount() > 0) return true;   // повтор робить сама програма
        f.win.key(code, down);
        return true;
    }

    private void superArrow(WinFrame f, int dir) {
        if (f.popup) return;
        int cur = f.win.state() == LinuxWindow.STATE_MAXIMIZED ? Snap.MAX : (f.zone == CUSTOM_ZONE ? Snap.NONE : f.zone);
        int nz = Snap.arrow(cur, dir);
        if (nz == -1) minimize(f); else if (nz == Snap.NONE) restoreFromSnap(f); else snap(f, nz);
    }

    void tapKey(WinFrame f, int code) { f.win.key(code, true); f.win.key(code, false); }

    /** Символ з екранної клавіатури: знайти клавішу в EN/УК, за потреби перемкнути групу й Shift. */
    void typeChar(WinFrame f, char c) {
        if (c == '\n') { tapKey(f, 28); return; }
        KeyTable.Hit h = KeyTable.find(c, group);
        if (h == null) return;
        if (h.group != group) setGroup(h.group);
        if (h.shift) f.win.key(42, true);
        f.win.key(h.code, true); f.win.key(h.code, false);
        if (h.shift) f.win.key(42, false);
    }

    // ---------------- Для дока та меню ----------------

    private List<WinFrame> appFrames(String key) {
        List<WinFrame> l = new ArrayList<>();
        for (WinFrame f : z) if (!f.popup && key != null && key.equals(f.win.appKey())) l.add(f);
        return l;
    }

    public int windowCount(String appKey) { return appFrames(appKey).size(); }
    public boolean isRunning(String appKey) { return windowCount(appKey) > 0; }
    public boolean isFocusedApp(String appKey) { return focused != null && appKey != null && appKey.equals(focused.win.appKey()); }

    public void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    private void changed() { for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    /** Клік на значок у доці: відновити / підняти / згорнути. */
    public void toggleApp(String key) {
        List<WinFrame> l = appFrames(key);
        if (l.isEmpty()) return;
        boolean anyHidden = false;
        for (WinFrame f : l) if (f.hidden) anyHidden = true;
        if (anyHidden) { for (WinFrame f : l) if (f.hidden) restore(f); return; }
        if (isFocusedApp(key)) { for (WinFrame f : l) minimize(f); return; }
        for (WinFrame f : l) raise(f);
        focusFrame(l.get(l.size() - 1));
    }

    public void closeApp(String key) { for (WinFrame f : appFrames(key)) f.win.requestClose(); }

    public void maximizeApp(String key) {
        List<WinFrame> l = appFrames(key);
        for (WinFrame f : l) { if (f.hidden) restore(f); snap(f, Snap.MAX); }
        if (!l.isEmpty()) { raise(l.get(l.size() - 1)); focusFrame(l.get(l.size() - 1)); }
    }

    // ---------------- Власні розкладки (для налаштувань) ----------------

    /** [id, назва, зони у відсотках "x y w h; ..."]. */
    public static List<Object[]> customLayouts() {
        List<Object[]> l = new ArrayList<>();
        for (Snap.Layout x : Snap.custom()) {
            StringBuilder b = new StringBuilder();
            for (float[] q : x.zones) {
                if (b.length() > 0) b.append("; ");
                b.append(Math.round(q[0] * 100)).append(' ').append(Math.round(q[1] * 100)).append(' ').append(Math.round(q[2] * 100)).append(' ').append(Math.round(q[3] * 100));
            }
            l.add(new Object[]{x.id, x.name, b.toString()});
        }
        return l;
    }

    public static boolean saveLayout(String id, String name, String zones) {
        List<float[]> z = Snap.parseZones(zones);
        if (z == null || name.isEmpty()) return false;
        List<Snap.Layout> all = Snap.custom(), out = new ArrayList<>();
        String nid = id != null ? id : "z" + Long.toString(System.currentTimeMillis(), 36);
        boolean found = false;
        for (Snap.Layout x : all) { if (x.id.equals(nid)) { out.add(new Snap.Layout(nid, name, z)); found = true; } else out.add(x); }
        if (!found) out.add(new Snap.Layout(nid, name, z));
        Snap.saveCustom(out);
        return true;
    }

    public static void deleteLayout(String id) {
        List<Snap.Layout> out = new ArrayList<>();
        for (Snap.Layout x : Snap.custom()) if (!x.id.equals(id)) out.add(x);
        Snap.saveCustom(out);
        if (id.equals(Prefs.getString(LinuxPrefs.ACTIVE_LAYOUT, ""))) Prefs.putString(LinuxPrefs.ACTIVE_LAYOUT, "");
    }

    // ---------------- Тестове джерело ----------------

    private FakeSource fake;
    public void spawnFake(String appKey, String name) {
        if (fake == null) { fake = new FakeSource(ctx); fake.setListener(this); sources.add(fake); }
        fake.setFps(LinuxPrefs.fps());
        pushOutput();
        fake.spawn(appKey, name);
    }
}
