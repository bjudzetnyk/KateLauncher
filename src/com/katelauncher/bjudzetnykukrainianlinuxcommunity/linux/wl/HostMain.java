package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Точка входу композитора. Запускається в Termux:
 *   CLASSPATH=base.apk app_process / com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.HostMain
 * Працює в процесі Termux (його uid), слухає Wayland-сокет, а кадри віддає лаунчеру через Binder
 * (спільна пам'ять = ParcelFileDescriptor на memfd). Усередині ще й менеджер сеансу (Session): він запускає й
 * веде облік Linux-програм, а сам хост живе, доки є програми чи вікна, і завершується після простою.
 * Жодних служб Android: це звичайний процес, який тримає «на плечах» скрипт запуску в Termux.
 */
public final class HostMain {
    private HostMain() {}

    private static IBinder sink;
    private static final List<Parcel> pending = new ArrayList<>();
    private static final Object LOCK = new Object();
    private static String pkg, token;
    private static Handler handler;
    private static volatile boolean quitting;
    private static Session session;
    private static final Set<Integer> winIds = new HashSet<>();
    private static final Map<Integer, String> winTags = new HashMap<>();
    private static long noSinkSince;

    public static void main(String[] args) {
        pkg = env("KATE_PKG", null);
        token = env("KATE_TOKEN", "");
        String dir = env("KATE_DIR", "/data/data/com.termux/files/usr/tmp/katewl");
        if (pkg == null) { System.err.println("KATE_PKG не задано"); System.exit(2); }
        if (!Native.load()) { System.err.println("Не вдалося завантажити libkatewl.so"); System.exit(3); }
        Looper.prepare();
        handler = new Handler();
        if (!Native.nativeStart(dir, "wayland-kate", KeyTable.keymap(), 800, 600, 1, 30)) {
            System.err.println("Не вдалося створити Wayland-сокет у " + dir);
            System.exit(4);
        }
        System.err.println("katewl host: pid=" + Process.myPid() + " dir=" + dir + " proto=" + Proto.VERSION);
        Native.cmd(Native.CMD_IDLE, 0, 0, 0, 0, 0, 0, null);   // тривалість сеансу вирішує Session, а не композитор
        session = new Session(new File(dir));
        session.start();
        noSinkSince = System.currentTimeMillis();
        kick();
        Looper.loop();
    }


    // ---------- Пошук лаунчера: broadcast повторюється, доки немає з'єднання; будиться й новою подією ----------
    private static boolean looping;   // лише потік handler
    private static int tries;
    private static final Runnable PUMP = new Runnable() {
        @Override public void run() {
            if (quitting) { looping = false; return; }
            synchronized (LOCK) { if (sink != null) { looping = false; tries = 0; noSinkSince = 0; return; } }
            if (noSinkSince == 0) noSinkSince = System.currentTimeMillis();
            // Лаунчера немає (закрився, оновився…): програми продовжують працювати, а ми раз на кілька секунд
            // стукаємо знову. Виходимо лише коли нікому й нічого показувати.
            boolean busy = (session != null && session.activeApps() > 0) || windowCount() > 0;
            if (!busy && System.currentTimeMillis() - noSinkSince > 90000) { System.err.println("Лаунчер не відповів, програм немає — вихід"); quit(); return; }
            tries++;
            broadcast();
            handler.postDelayed(this, tries < 30 ? 1500 : 5000);
        }
    };
    private static void kick() {
        Handler h = handler;
        if (h == null) return;
        h.post(new Runnable() { @Override public void run() { if (!looping) { looping = true; tries = 0; PUMP.run(); } } });
    }

    private static String env(String k, String d) { String v = System.getenv(k); return v == null || v.isEmpty() ? d : v; }

    // ---------- Binder сервер композитора ----------
    private static final Binder HOST = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == Proto.H_ATTACH) {
                data.enforceInterface(Proto.HOST_DESCRIPTOR);
                String t = data.readString();
                if (!token.equals(t)) return true;
                IBinder s = data.readStrongBinder();
                int w = data.readInt(), h = data.readInt(), sc = data.readInt(), fps = data.readInt();
                if (data.dataAvail() >= 8) {
                    int idle = data.readInt(), dbg = data.readInt();
                    if (session != null) session.setIdleMinutes(idle);
                    Native.cmd(Native.CMD_DEBUG, dbg, 0, 0, 0, 0, 0, null);
                }
                Native.cmd(Native.CMD_OUTPUT, w, h, sc, 0, 0, 0, null);
                Native.cmd(Native.CMD_FPS, fps, 0, 0, 0, 0, 0, null);
                attach(s);
                return true;
            } else if (code == Proto.H_CMD) {
                data.enforceInterface(Proto.HOST_DESCRIPTOR);
                int op = data.readInt(), a = data.readInt(), b = data.readInt(), c = data.readInt(), d = data.readInt();
                double x = data.readDouble(), y = data.readDouble();
                String s = data.readString();
                if (op == Native.CMD_STOP) { requestStop(); return true; }
                if (op >= 100) { session(op, a, s); return true; }
                Native.cmd(op, a, b, c, d, x, y, s);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private static void attach(final IBinder s) {
        try {
            s.linkToDeath(new IBinder.DeathRecipient() {
                @Override public void binderDied() {
                    System.err.println("Лаунчер завершився — програми лишаються, чекаю на повернення");
                    synchronized (LOCK) { sink = null; }
                    noSinkSince = System.currentTimeMillis();
                    kick();
                }
            }, 0);
        } catch (RemoteException e) { return; }
        List<Parcel> flush;
        synchronized (LOCK) {
            sink = s; noSinkSince = 0;
            // події, накопичені за відсутності лаунчера, застаріли: нове підключення починає з повного оголошення вікон
            flush = new ArrayList<>(pending); pending.clear();
        }
        for (Parcel p : flush) p.recycle();
        Native.cmd(Native.CMD_RESYNC, 0, 0, 0, 0, 0, 0, null);
        if (session != null) session.resend();
    }

    // ---------- Команди менеджеру сеансу ----------
    private static void session(int op, int a, String s) {
        Session ss = session; if (ss == null) return;
        switch (op) {
        case Native.SES_LAUNCH: if (s != null) ss.submit(s); break;
        case Native.SES_KILL_APP: if (s != null) ss.killApp(s); break;
        case Native.SES_KILL_ALL: ss.killAll(a != 0); break;
        case Native.SES_STOP: requestStop(); break;
        case Native.SES_GET_LOG: if (s != null) cbLog(s, ss.readLog(s)); break;
        case Native.SES_LIST: ss.resend(); break;
        case Native.SES_IDLE: ss.setIdleMinutes(a); break;
        case Native.SES_DEBUG: Native.cmd(Native.CMD_DEBUG, a, 0, 0, 0, 0, 0, null); break;
        }
    }

    // ---------- Для Session ----------
    static int windowCount() { synchronized (winIds) { return winIds.size(); } }
    static boolean hasWindowsFor(String app) { synchronized (winIds) { for (String t : winTags.values()) if (t.equals(app)) return true; } return false; }
    static void activity(int seconds) { Native.cmd(Native.CMD_ACTIVITY, seconds, 0, 0, 0, 0, 0, null); }
    /** Завершити сеанс: усе вбити й вийти. Викликається з будь-якого потоку. */
    static void requestStop() {
        new Thread(new Runnable() { @Override public void run() {
            Session ss = session;
            if (ss != null) ss.shutdown();
            Native.cmd(Native.CMD_STOP, 0, 0, 0, 0, 0, 0, null);
        } }).start();
    }

    // ---------- Broadcast із Binder-ом до лаунчера (як у Termux:X11: через системний контекст) ----------
    private static void broadcast() {
        try {
            Object at = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            Context ctx = (Context) at.getClass().getMethod("getSystemContext").invoke(at);
            Intent i = new Intent(pkg + ".WL_HOST").setPackage(pkg);
            Bundle b = new Bundle();
            b.putBinder("binder", HOST);
            b.putString("token", token);
            b.putInt("proto", Proto.VERSION);
            i.putExtra("kwl", b);
            int uid = Process.myUid();
            if (uid == 0 || uid == 2000) i.setFlags(0x00400000);   // FLAG_RECEIVER_FROM_SHELL
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            System.err.println("broadcast не вдався: " + t);
            t.printStackTrace();
        }
    }

    // ---------- Відправка подій лаунчеру ----------
    private static Parcel obtain(int code) {
        Parcel p = Parcel.obtain();
        p.writeInt(code);
        return p;
    }

    private static void send(Parcel p) {
        synchronized (LOCK) {
            if (sink == null) { pending.add(p); if (pending.size() > 4000) pending.remove(0).recycle(); kick(); return; }
        }
        deliver(p);
    }

    private static void deliver(Parcel p) {
        IBinder s;
        synchronized (LOCK) { s = sink; }
        try {
            p.setDataPosition(0);
            int code = p.readInt();
            Parcel d = Parcel.obtain();
            d.writeInterfaceToken(Proto.SINK_DESCRIPTOR);
            d.appendFrom(p, p.dataPosition(), p.dataSize() - p.dataPosition());
            try { if (s != null) s.transact(code, d, null, IBinder.FLAG_ONEWAY); } finally { d.recycle(); }
        } catch (DeadObjectException e) {
            synchronized (LOCK) { if (sink == s) sink = null; }
        } catch (Throwable t) {
            System.err.println("transact: " + t);
        } finally { p.recycle(); }
    }

    // ---------- Колбеки з C (потік композитора) ----------
    public static void cbWindowAdd(int id, int kind, int parent, int px, int py, int w, int h, int scale, String tag, String appId, String title, boolean deco) {
        synchronized (winIds) { winIds.add(id); if (tag != null && !tag.isEmpty() && kind != 1 && kind != 2) winTags.put(id, tag); }
        if (session != null) session.markWindow(tag);
        Parcel p = obtain(Proto.S_ADD);
        p.writeInt(id); p.writeInt(kind); p.writeInt(parent); p.writeInt(px); p.writeInt(py); p.writeInt(w); p.writeInt(h); p.writeInt(scale);
        p.writeString(tag); p.writeString(appId); p.writeString(title); p.writeInt(deco ? 1 : 0);
        send(p);
    }

    public static void cbBuffer(int id, int gen, int w, int h, int scale, int fd, int size) {
        Parcel p = obtain(Proto.S_BUFFER);
        p.writeInt(id); p.writeInt(gen); p.writeInt(w); p.writeInt(h); p.writeInt(scale); p.writeInt(size);
        ParcelFileDescriptor pfd = ParcelFileDescriptor.adoptFd(fd);
        pfd.writeToParcel(p, 0);
        try { pfd.close(); } catch (Exception ignored) {}
        send(p);
    }

    public static void cbDamage(int id, int gen, int x, int y, int w, int h) {
        Parcel p = obtain(Proto.S_DAMAGE);
        p.writeInt(id); p.writeInt(gen); p.writeInt(x); p.writeInt(y); p.writeInt(w); p.writeInt(h);
        send(p);
    }

    public static void cbTitle(int id, String t) { Parcel p = obtain(Proto.S_TITLE); p.writeInt(id); p.writeString(t); send(p); }
    public static void cbAppId(int id, String t) { Parcel p = obtain(Proto.S_APPID); p.writeInt(id); p.writeString(t); send(p); }
    public static void cbHints(int id, int a, int b, int c, int d) { Parcel p = obtain(Proto.S_HINTS); p.writeInt(id); p.writeInt(a); p.writeInt(b); p.writeInt(c); p.writeInt(d); send(p); }
    public static void cbRemove(int id) { synchronized (winIds) { winIds.remove(id); winTags.remove(id); } Parcel p = obtain(Proto.S_REMOVE); p.writeInt(id); send(p); }
    public static void cbRequest(int id, int kind, int arg) { Parcel p = obtain(Proto.S_REQUEST); p.writeInt(id); p.writeInt(kind); p.writeInt(arg); send(p); }
    public static void cbPopup(int id, int x, int y, int w, int h) { Parcel p = obtain(Proto.S_POPUP); p.writeInt(id); p.writeInt(x); p.writeInt(y); p.writeInt(w); p.writeInt(h); send(p); }
    public static void cbRun(String runId, String appId, int state, int code, int pid, boolean service, String name) {
        Parcel p = obtain(Proto.S_RUN);
        p.writeString(runId); p.writeString(appId); p.writeInt(state); p.writeInt(code); p.writeInt(pid); p.writeInt(service ? 1 : 0); p.writeString(name);
        send(p);
    }
    public static void cbLog(String name, String text) { Parcel p = obtain(Proto.S_LOG); p.writeString(name); p.writeString(text); send(p); }
    public static void cbClipboard(String t) { Parcel p = obtain(Proto.S_CLIPBOARD); p.writeString(t); send(p); }

    public static void cbQuit() {
        Parcel p = obtain(Proto.S_QUIT);
        send(p);
        quit();
    }

    private static void quit() {
        quitting = true;
        try { if (session != null) session.shutdown(); } catch (Throwable ignored) {}
        // дати Binder-у змогу доставити S_QUIT
        new Thread(new Runnable() { @Override public void run() {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            System.exit(0);
        } }).start();
    }
}
