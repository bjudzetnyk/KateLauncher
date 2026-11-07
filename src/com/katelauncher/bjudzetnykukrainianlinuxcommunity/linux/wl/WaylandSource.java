package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

import android.graphics.Rect;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxPrefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.SessionState;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.LinuxBuffer;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.LinuxWindow;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.WindowSource;

/** Сторона лаунчера: приймає вікна від композитора в Termux (Binder) і віддає їх менеджеру вікон. */
public final class WaylandSource implements WindowSource {
    private static final WaylandSource INSTANCE = new WaylandSource();
    public static WaylandSource get() { return INSTANCE; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, WlWindow> wins = new HashMap<>();
    private Listener listener;
    private IBinder host;
    private int outW = 800, outH = 600, outScale = 1, fps = 30;
    private String lastClip;
    private static volatile String lastLaunched; private static volatile long lastLaunchedAt;

    private WaylandSource() {}

    public static void noteLaunch(String appId) { lastLaunched = appId; lastLaunchedAt = System.currentTimeMillis(); }

    @Override public void setListener(Listener l) { listener = l; }
    public boolean isConnected() { return host != null && !stale; }

    // ---------- Підключення ----------
    public void onHost(final IBinder h) {
        main.post(new Runnable() { @Override public void run() {
            if (host == h) return;   // повторний broadcast того самого хоста
            if (host != null) { try { host.unlinkToDeath(death, 0); } catch (Exception ignored) {} }
            host = h; stale = false;
            try { h.linkToDeath(death, 0); } catch (RemoteException e) { host = null; return; }
            // Нове підключення: хост оголосить усі вікна наново, тож стару картину скидаємо.
            dropAllWindows(false);
            SessionState.setHostAlive(true);
            Parcel p = Parcel.obtain();
            try {
                p.writeInterfaceToken(Proto.HOST_DESCRIPTOR);
                p.writeString(com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxPrefs.token());
                p.writeStrongBinder(sink);
                p.writeInt(outW); p.writeInt(outH); p.writeInt(outScale); p.writeInt(fps);
                p.writeInt(LinuxPrefs.idleMinutes()); p.writeInt(LinuxPrefs.debug() ? 1 : 0);
                h.transact(Proto.H_ATTACH, p, null, IBinder.FLAG_ONEWAY);
            } catch (RemoteException e) { host = null; } finally { p.recycle(); }
        } });
    }

    private final IBinder.DeathRecipient death = new IBinder.DeathRecipient() {
        @Override public void binderDied() { main.post(new Runnable() { @Override public void run() { gone(); } }); }
    };

    private void gone() {
        host = null;
        dropAllWindows(true);
        SessionState.setHostAlive(false);
    }

    private void dropAllWindows(boolean notifyGone) {
        List<WlWindow> l = new ArrayList<>(wins.values());
        wins.clear();
        for (WlWindow w : l) { w.release(); if (listener != null) listener.onWindowRemoved(w); }
        if (notifyGone && listener != null) listener.onSourceGone(this);
    }

    /** Хост від попередньої збірки лаунчера (інший протокол): не підключаємось, а новий запуск замінить його. */
    public void staleHost() { stale = true; }
    public boolean isStale() { return stale; }
    private volatile boolean stale;

    /** Команда менеджеру сеансу (Session у хості). */
    public void sessionCmd(int op, int a, String s) { cmd(op, a, 0, 0, 0, 0, 0, s); }

    // ---------- Команди композитору ----------
    void cmd(int op, int a, int b, int c, int d, double x, double y, String s) {
        IBinder h = host;
        if (h == null) return;
        Parcel p = Parcel.obtain();
        try {
            p.writeInterfaceToken(Proto.HOST_DESCRIPTOR);
            p.writeInt(op); p.writeInt(a); p.writeInt(b); p.writeInt(c); p.writeInt(d); p.writeDouble(x); p.writeDouble(y); p.writeString(s);
            h.transact(Proto.H_CMD, p, null, IBinder.FLAG_ONEWAY);
        } catch (DeadObjectException e) { host = null; } catch (RemoteException ignored) {} finally { p.recycle(); }
    }

    @Override public void setOutput(int w, int h, int scale) { outW = w; outH = h; outScale = scale; cmd(Native.CMD_OUTPUT, w, h, scale, 0, 0, 0, null); }
    @Override public void setFps(int f) { fps = f; cmd(Native.CMD_FPS, f, 0, 0, 0, 0, 0, null); }
    @Override public void setFocus(LinuxWindow w) { cmd(Native.CMD_FOCUS, w == null ? 0 : w.id(), 0, 0, 0, 0, 0, null); }
    @Override public void setGroup(int g) { cmd(Native.CMD_GROUP, g, 0, 0, 0, 0, 0, null); }
    @Override public void setClipboard(String t) { if (t == null || t.equals(lastClip)) return; lastClip = t; cmd(Native.CMD_CLIPBOARD, 0, 0, 0, 0, 0, 0, t); }
    @Override public void stop() { cmd(Native.CMD_STOP, 0, 0, 0, 0, 0, 0, null); }

    // ---------- Події від композитора (потік Binder -> головний потік, зберігаючи порядок) ----------
    private final Binder sink = new Binder() {
        @Override protected boolean onTransact(int code, Parcel d, Parcel reply, int flags) throws RemoteException {
            if (code < Proto.S_ADD || code > Proto.S_LAST) return super.onTransact(code, d, reply, flags);
            try { return handle(code, d); } catch (Throwable t) { return true; }
        }
        private boolean handle(int code, Parcel d) {
            d.enforceInterface(Proto.SINK_DESCRIPTOR);
            switch (code) {
            case Proto.S_ADD: {
                final int id = d.readInt(), kind = d.readInt(), parent = d.readInt(), px = d.readInt(), py = d.readInt(), w = d.readInt(), h = d.readInt(), sc = d.readInt();
                final String tag = d.readString(), appId = d.readString(), title = d.readString(); final boolean deco = d.readInt() != 0;
                main.post(new Runnable() { @Override public void run() { onAdd(id, kind, parent, px, py, w, h, sc, tag, appId, title, deco); } });
                break; }
            case Proto.S_BUFFER: {
                final int id = d.readInt(), gen = d.readInt(), w = d.readInt(), h = d.readInt(), sc = d.readInt(), size = d.readInt();
                final ParcelFileDescriptor pfd = ParcelFileDescriptor.CREATOR.createFromParcel(d);
                main.post(new Runnable() { @Override public void run() { onBuffer(id, gen, w, h, sc, size, pfd); } });
                break; }
            case Proto.S_DAMAGE: {
                final int id = d.readInt(), gen = d.readInt(), x = d.readInt(), y = d.readInt(), w = d.readInt(), h = d.readInt();
                main.post(new Runnable() { @Override public void run() { onDamage(id, gen, x, y, w, h); } });
                break; }
            case Proto.S_TITLE: { final int id = d.readInt(); final String t = d.readString();
                main.post(new Runnable() { @Override public void run() { WlWindow w = wins.get(id); if (w != null) { w.title = t; changed(w); } } }); break; }
            case Proto.S_APPID: { final int id = d.readInt(); final String t = d.readString();
                main.post(new Runnable() { @Override public void run() { WlWindow w = wins.get(id); if (w != null) { w.appId = t; changed(w); } } }); break; }
            case Proto.S_HINTS: { final int id = d.readInt(), a = d.readInt(), b = d.readInt(), c = d.readInt(), e = d.readInt();
                main.post(new Runnable() { @Override public void run() { WlWindow w = wins.get(id); if (w != null) { w.minW = a; w.minH = b; changed(w); } } }); break; }
            case Proto.S_REMOVE: { final int id = d.readInt();
                main.post(new Runnable() { @Override public void run() { onRemove(id); } }); break; }
            case Proto.S_REQUEST: { final int id = d.readInt(), k = d.readInt(), a = d.readInt();
                main.post(new Runnable() { @Override public void run() { WlWindow w = wins.get(id); if (w != null && listener != null) listener.onWindowRequest(w, k, a); } }); break; }
            case Proto.S_POPUP: { final int id = d.readInt(), x = d.readInt(), y = d.readInt();
                main.post(new Runnable() { @Override public void run() { WlWindow w = wins.get(id); if (w != null) { w.px = x; w.py = y; if (listener != null) listener.onPopupMoved(w); } } }); break; }
            case Proto.S_CLIPBOARD: { final String t = d.readString();
                main.post(new Runnable() { @Override public void run() { lastClip = t; if (listener != null) listener.onClipboard(t); } }); break; }
            case Proto.S_RUN: {
                final String runId = d.readString(), app = d.readString(); final int state = d.readInt(), exit = d.readInt(), pid = d.readInt(); final boolean svc = d.readInt() != 0; final String name = d.readString();
                main.post(new Runnable() { @Override public void run() { SessionState.onRun(runId, app, state, exit, pid, svc, name, hasWindowFor(app)); } }); break; }
            case Proto.S_LOG: { final String n = d.readString(), t = d.readString();
                main.post(new Runnable() { @Override public void run() { SessionState.onLog(n, t); } }); break; }
            case Proto.S_QUIT:
                main.post(new Runnable() { @Override public void run() { gone(); } }); break;
            }
            return true;
        }
    };

    private boolean hasWindowFor(String app) {
        String k = "linux/" + app;
        for (WlWindow x : wins.values()) if (k.equals(x.appKey)) return true;
        return false;
    }

    private void changed(WlWindow w) { if (listener != null) listener.onWindowChanged(w); }

    private void onAdd(int id, int kind, int parent, int px, int py, int w, int h, int sc, String tag, String appId, String title, boolean deco) {
        WlWindow win = new WlWindow(this, id);
        win.kind = kind; win.parentId = parent; win.px = px; win.py = py; win.w = w; win.h = h; win.sc = sc; win.appId = appId; win.title = title; win.deco = deco;
        if (tag != null && !tag.isEmpty()) win.appKey = "linux/" + tag;
        else if (lastLaunched != null && System.currentTimeMillis() - lastLaunchedAt < 20000) win.appKey = "linux/" + lastLaunched;
        wins.put(id, win);
        // додавання до менеджера — після першого буфера (щоб одразу мати кадр)
    }

    private void onBuffer(int id, int gen, int w, int h, int sc, int size, ParcelFileDescriptor pfd) {
        WlWindow win = wins.get(id);
        if (win == null || !Native.load()) { try { pfd.close(); } catch (Exception ignored) {} return; }
        long ptr = 0;
        try { ptr = Native.nativeMap(pfd.getFd(), size); } catch (Throwable t) { ptr = 0; }
        try { pfd.close(); } catch (Exception ignored) {}
        if (ptr == 0) return;
        boolean first = win.buffer == null;
        win.release();
        win.ptr = ptr; win.size = size; win.gen = gen; win.rw = w; win.rh = h; win.sc = Math.max(1, sc);
        win.w = w / win.sc; win.h = h / win.sc;
        win.buffer = new LinuxBuffer(w, h);
        win.buffer.blitNative(ptr, w, 0, 0, w, h);
        if (listener == null) return;
        if (first) listener.onWindowAdded(win); else listener.onWindowChanged(win);
    }

    private void onDamage(int id, int gen, int x, int y, int w, int h) {
        WlWindow win = wins.get(id);
        if (win == null || win.buffer == null || gen != win.gen) return;
        win.buffer.blitNative(win.ptr, win.rw, x, y, w, h);
        if (listener != null) listener.onWindowDamage(win, new Rect(x, y, x + w, y + h));
    }

    private void onRemove(int id) {
        WlWindow win = wins.remove(id);
        if (win == null) return;
        boolean shown = win.buffer != null;
        win.release();
        if (shown && listener != null) listener.onWindowRemoved(win);
    }

    WlWindow find(int id) { return wins.get(id); }
}
