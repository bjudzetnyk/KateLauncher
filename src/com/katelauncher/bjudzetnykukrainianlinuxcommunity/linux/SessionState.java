package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

/** Те, що лаунчер знає про сеанс у Termux: список запусків і журнали (оновлюється подіями від хоста). Головний потік. */
public final class SessionState {
    private SessionState() {}

    public static final int ST_START = 0, ST_RUN = 1, ST_EXIT = 2, ST_KILLED = 3;

    public static final class RunInfo {
        public String runId, app, name; public int state, code, pid; public boolean service;
        public long updated = System.currentTimeMillis();
    }

    public interface Listener { void onRuns(); void onLog(String name, String text); }

    private static final Map<String, RunInfo> runs = new LinkedHashMap<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Context ctx;
    private static boolean hostAlive;

    public static void init(Context c) { if (c != null) ctx = c.getApplicationContext(); }
    public static void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }
    public static boolean hostAlive() { return hostAlive; }
    public static void setHostAlive(boolean b) { hostAlive = b; if (!b) { runs.clear(); } fire(); }

    public static List<RunInfo> list() { return new ArrayList<>(runs.values()); }

    public static int activeCount() {
        int n = 0;
        for (RunInfo r : runs.values()) if (!r.service && r.state <= ST_RUN) n++;
        return n;
    }

    /** Подія від хоста. hasWindow — чи з'явилось вікно в цієї програми (для вирішення «зникла мовчки чи впала»). */
    public static void onRun(String runId, String app, int state, int code, int pid, boolean service, String name, boolean hasWindow) {
        RunInfo r = runs.get(runId);
        if (r == null) { r = new RunInfo(); r.runId = runId; r.app = app; runs.put(runId, r); }
        r.name = name == null || name.isEmpty() ? app : name; r.state = state; r.code = code; r.pid = pid; r.service = service; r.updated = System.currentTimeMillis();
        if (state > ST_RUN && ctx != null) {
            if (service && code != 0 && state == ST_EXIT) {
                String msg = code == 127 ? I18n.t("lx_err_xwayland_missing") : I18n.t("lx_err_xwayland_died").replace("%1", String.valueOf(code));
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
            } else if (!service && state == ST_EXIT && code != 0 && !hasWindow) {
                Toast.makeText(ctx, I18n.t("lx_err_app_exit").replace("%1", r.name).replace("%2", String.valueOf(code)), Toast.LENGTH_LONG).show();
            }
        }
        if (state > ST_RUN) { final String id = runId; main.postDelayed(new Runnable() { @Override public void run() { runs.remove(id); fire(); } }, 20000); }
        fire();
    }

    public static void onLog(String name, String text) { for (Listener l : new ArrayList<>(listeners)) l.onLog(name, text); }

    private static void fire() { for (Listener l : new ArrayList<>(listeners)) l.onRuns(); }
}
