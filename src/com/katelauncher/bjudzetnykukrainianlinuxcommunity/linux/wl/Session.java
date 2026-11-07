package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Менеджер сеансу. Живе в процесі-хості (Termux), поруч із композитором, і замінює старий підхід
 * «вистрілив і забув»:
 *  - приймає запити на запуск (файлом у ctl/ або через Binder), запускає програми як дочірні процеси,
 *    веде облік (pid, стан, код виходу), пише окремий журнал на кожну програму;
 *  - КОЖЕН процес запуску несе мітку середовища KATE_RUN=&lt;id&gt;, тож його (і все, що він породив,
 *    навіть через proot) можна знайти та завершити за міткою, а не за PID-ом;
 *  - при старті нового хоста вбиває залишки попереднього сеансу (проти «Firefox уже запущено»);
 *  - тримає службу Xwayland для режиму X11 й підключає до неї XWM композитора;
 *  - сам завершує сеанс після простою (налаштовується), щоб не тримати Termux даремно.
 */
final class Session {
    static final int ST_START = 0, ST_RUN = 1, ST_EXIT = 2, ST_KILLED = 3;
    static final String XWL_ID = "xwl";
    static final String X_DISPLAY = "42";

    static final class Run {
        final String id, app, name; final boolean service;
        volatile Process proc; volatile int state = ST_START, code, pid;
        volatile boolean sawWindow, killedByUs;
        long start;
        File log;
        Run(String id, String app, String name, boolean service) { this.id = id; this.app = app; this.name = name; this.service = service; }
    }

    /** Розібраний запит на запуск. */
    private static final class Req {
        String runId = "", app = "", name = "", display = "wayland";
        final Map<String, String> sec = new HashMap<>();
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File dir, ctl, logs, rundir;
    private final String sh, prefix, tmpRoot;
    private final File home;
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final Object lock = new Object();
    private volatile boolean stopping;
    private volatile int idleMin = 5;
    private long idleSince;
    private boolean xwmArmed;
    private final java.util.concurrent.CountDownLatch swept = new java.util.concurrent.CountDownLatch(1);

    Session(File dir) {
        this.dir = dir;
        ctl = new File(dir, "ctl"); logs = new File(dir, "logs"); rundir = new File(dir, "run");
        ctl.mkdirs(); logs.mkdirs(); rundir.mkdirs();
        String pf = System.getenv("PREFIX");
        prefix = pf != null && !pf.isEmpty() ? pf : "/data/data/com.termux/files/usr";
        sh = prefix + "/bin/sh";
        File par = dir.getParentFile();
        tmpRoot = par != null ? par.getPath() : prefix + "/tmp";
        String hm = System.getenv("HOME");
        home = new File(hm != null && !hm.isEmpty() ? hm : "/data/data/com.termux/files/home");
    }

    void setIdleMinutes(int m) { idleMin = Math.max(0, m); }

    // ------------------------------------------------------------------ життєвий цикл

    void start() {
        Thread t = new Thread(new Runnable() { @Override public void run() { loop(); } }, "kate-session");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        try { sweepOrphans(); } catch (Throwable t) { System.err.println("sweep: " + t); }
        swept.countDown();
        long lastIdleCheck = 0;
        while (!stopping) {
            try {
                File[] fs = ctl.listFiles();
                if (fs != null) {
                    Arrays.sort(fs);
                    for (File f : fs) if (f.getName().endsWith(".req")) { handleFile(f); }
                }
            } catch (Throwable t) { System.err.println("session loop: " + t); }
            long now = System.currentTimeMillis();
            if (now - lastIdleCheck > 3000) { lastIdleCheck = now; idleCheck(now); }
            try { Thread.sleep(150); } catch (InterruptedException e) { return; }
        }
    }

    private void idleCheck(long now) {
        int m = idleMin;
        if (m <= 0) { idleSince = 0; return; }
        if (activeApps() > 0 || HostMain.windowCount() > 0) { idleSince = 0; return; }
        if (idleSince == 0) { idleSince = now; return; }
        if (now - idleSince >= m * 60000L) {
            System.err.println("Сеанс простоює " + m + " хв — завершую");
            HostMain.requestStop();
        }
    }

    /** Кількість живих користувацьких програм (без служб). */
    int activeApps() {
        int n = 0;
        synchronized (lock) { for (Run r : runs.values()) if (!r.service && r.state <= ST_RUN) n++; }
        return n;
    }

    /** Завершити все, що запустили (викликається перед виходом хоста). */
    void shutdown() {
        stopping = true;
        killAllNow();
        new File(tmpRoot, ".X11-unix/X" + X_DISPLAY).delete();
        new File(tmpRoot, ".X" + X_DISPLAY + "-lock").delete();
    }

    // ------------------------------------------------------------------ запити

    private void handleFile(File f) {
        long age = System.currentTimeMillis() - f.lastModified();
        String text = null;
        try { text = new String(readAll(new FileInputStream(f), 1 << 20), UTF8); } catch (IOException ignored) {}
        f.delete();
        if (text == null) return;
        if (age > 120000) { System.err.println("Застарілий запит " + f.getName() + " пропущено"); return; }
        handleRequest(text);
    }

    /** Точка входу з Binder-а (лаунчер уже підключений). */
    void submit(final String text) {
        new Thread(new Runnable() { @Override public void run() { try { handleRequest(text); } catch (Throwable t) { System.err.println("submit: " + t); } } }).start();
    }

    private Req parse(String text) {
        Req r = new Req();
        String cur = null; StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("#KATE-SEC ")) {
                if (cur != null) r.sec.put(cur, sb.toString());
                cur = line.substring(10).trim(); sb.setLength(0);
            } else if (cur != null) sb.append(line).append('\n');
            else {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                if (k.equals("run")) r.runId = v; else if (k.equals("app")) r.app = v;
                else if (k.equals("name")) { try { r.name = java.net.URLDecoder.decode(v, "UTF-8"); } catch (Exception e) { r.name = v; } }
                else if (k.equals("display")) r.display = v;
            }
        }
        if (cur != null) r.sec.put(cur, sb.toString());
        return r;
    }

    private void handleRequest(String text) {
        try { swept.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        Req r = parse(text);
        if (r.runId.isEmpty() || !r.runId.matches("[A-Za-z0-9_-]{1,40}") || r.app.isEmpty()) { System.err.println("Некоректний запит"); return; }
        String app = r.sec.get("app");
        if (app == null) { System.err.println("Запит без розділу app"); return; }
        HostMain.activity(60);
        if ("x11".equals(r.display)) {
            String x = r.sec.get("xwl");
            if (x == null || !ensureXwayland(x)) {
                final Run bad = new Run(r.runId, r.app, r.name, false);
                bad.state = ST_EXIT; bad.code = 127;
                HostMain.cbRun(bad.id, bad.app, ST_EXIT, 127, 0, false, "Xwayland");
                return;
            }
        }
        // Якщо ця програма вже запущена тим самим id — це нова копія, стару не чіпаємо.
        Run run = new Run(r.runId, r.app, r.name, false);
        File script = new File(rundir, r.runId + ".sh");
        try { writeFile(script, app); } catch (IOException e) { System.err.println("Не вдалося записати " + script); return; }
        File log = new File(logs, "app-" + safe(r.app) + ".log");
        run.log = log;
        String head = "# " + new java.util.Date() + "  програма=" + r.app + "  режим=" + r.display + "  run=" + r.runId + "\n# ---- команда ----\n" + app + "# ---- вивід ----\n";
        try { writeFile(log, head); } catch (IOException ignored) {}
        startProcess(run, script, log);
    }

    private static String safe(String s) { return s.replaceAll("[^A-Za-z0-9_-]", "_"); }

    // ------------------------------------------------------------------ Xwayland

    private boolean ensureXwayland(String script) {
        Run x;
        synchronized (lock) { x = runs.get(XWL_ID); }
        File sock = new File(tmpRoot, ".X11-unix/X" + X_DISPLAY);
        if (x != null && x.state <= ST_RUN && x.proc != null) {
            if (!xwmArmed) armXwm(sock);
            return true;
        }
        killTag(XWL_ID, 500);
        sock.delete(); new File(tmpRoot, ".X" + X_DISPLAY + "-lock").delete();
        new File(tmpRoot, ".X11-unix").mkdirs();
        Run run = new Run(XWL_ID, "@xwayland", "Xwayland", true);
        File sc = new File(rundir, XWL_ID + ".sh");
        File log = new File(logs, "x11.log");
        run.log = log;
        try { writeFile(sc, script); writeFile(log, "# " + new java.util.Date() + "  Xwayland :" + X_DISPLAY + "\n"); } catch (IOException e) { return false; }
        startProcess(run, sc, log);
        armXwm(sock);
        return true;
    }

    private void armXwm(File sock) {
        xwmArmed = true;
        Native.cmd(Native.CMD_XWM, 0, 0, 0, 0, 0, 0, sock.getPath());
    }

    // ------------------------------------------------------------------ процеси

    private void fixEnv(Map<String, String> env) {
        String ldp = System.getenv("KATE_ORIG_LD_PRELOAD");
        if (ldp != null && !ldp.isEmpty()) env.put("LD_PRELOAD", ldp); else env.remove("LD_PRELOAD");
        String ldl = System.getenv("KATE_ORIG_LD_LIBRARY_PATH");
        if (ldl != null && !ldl.isEmpty()) env.put("LD_LIBRARY_PATH", ldl); else env.remove("LD_LIBRARY_PATH");
        env.remove("CLASSPATH");
        env.remove("KATE_TOKEN"); env.remove("KATE_LIB"); env.remove("KATE_PKG");
        env.remove("KATE_ORIG_LD_PRELOAD"); env.remove("KATE_ORIG_LD_LIBRARY_PATH");
    }

    private void startProcess(final Run run, File script, File log) {
        try {
            ProcessBuilder pb = new ProcessBuilder(sh, script.getPath());
            pb.directory(home.isDirectory() ? home : new File("/"));
            Map<String, String> env = pb.environment();
            fixEnv(env);
            env.put("KATE_RUN", run.id);
            env.put("KATE_APP_ID", run.app);
            env.put("KATE_DIR", dir.getPath());
            env.put("KATE_DISPLAY_NUM", X_DISPLAY);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            run.start = System.currentTimeMillis();
            Process p = pb.start();
            run.proc = p; run.pid = pidOf(p); run.state = ST_RUN;
            synchronized (lock) { runs.put(run.id, run); }
            HostMain.cbRun(run.id, run.app, ST_RUN, 0, run.pid, run.service, run.name);
            Thread w = new Thread(new Runnable() { @Override public void run() { waitFor(run); } }, "kate-wait-" + run.id);
            w.setDaemon(true); w.start();
        } catch (Throwable t) {
            System.err.println("Не вдалося запустити " + run.app + ": " + t);
            try { appendFile(log, "\n[не вдалося запустити: " + t + "]\n"); } catch (IOException ignored) {}
            run.state = ST_EXIT; run.code = 126;
            HostMain.cbRun(run.id, run.app, ST_EXIT, 126, 0, run.service, run.name);
        }
    }

    private void waitFor(Run run) {
        int code = -1;
        try { code = run.proc.waitFor(); } catch (InterruptedException ignored) {}
        run.code = code;
        run.state = run.killedByUs ? ST_KILLED : ST_EXIT;
        try { appendFile(run.log, "\n[завершено, код " + code + (run.killedByUs ? ", зупинено лаунчером" : "") + "]\n"); } catch (Exception ignored) {}
        new File(rundir, run.id + ".sh").delete();
        HostMain.cbRun(run.id, run.app, run.state, code, run.pid, run.service, run.name);
        if (run.service && XWL_ID.equals(run.id)) {
            xwmArmed = false;
            Native.cmd(Native.CMD_XWM_STOP, 0, 0, 0, 0, 0, 0, null);
        }
        // Безпритульні нащадки цього запуску (proot, Firefox…) теж не лишаємо.
        if (!stopping && !run.service) {
            final String id = run.id;
            new Thread(new Runnable() { @Override public void run() {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                // Програма могла «роздвоїтись» і лишитись жити (наприклад, Firefox віддає роботу вже запущеному) — не чіпаємо,
                // доки в неї є вікна. Інакше добиваємо залишки.
                if (!hasWindows(run.app)) killTag(id, 800);
            } }).start();
        }
        synchronized (lock) { if (run.state > ST_RUN) { runs.remove(run.id); } }
    }

    private boolean hasWindows(String app) { return HostMain.hasWindowsFor(app); }

    // ------------------------------------------------------------------ вбивство

    /** Знайти всі процеси, чиє оточення містить KATE_RUN=id. */
    private List<Integer> findTagged(String key, String value) {
        List<Integer> out = new ArrayList<>();
        File[] ps = new File("/proc").listFiles();
        if (ps == null) return out;
        int me = android.os.Process.myPid();
        byte[] needle = (key + "=" + (value == null ? "" : value)).getBytes(UTF8);
        for (File p : ps) {
            String n = p.getName();
            if (n.isEmpty() || n.charAt(0) < '0' || n.charAt(0) > '9') continue;
            int pid;
            try { pid = Integer.parseInt(n); } catch (NumberFormatException e) { continue; }
            if (pid == me) continue;
            byte[] env;
            try { env = readAll(new FileInputStream(new File(p, "environ")), 1 << 18); } catch (Exception e) { continue; }
            if (hasVar(env, needle, value == null)) out.add(pid);
        }
        return out;
    }

    private static boolean hasVar(byte[] hay, byte[] needle, boolean prefixOnly) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            if (i > 0 && hay[i - 1] != 0) continue;
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            int e = i + needle.length;
            if (prefixOnly || e == hay.length || hay[e] == 0) return true;
        }
        return false;
    }

    private static void signal(List<Integer> pids, int sig) {
        for (int pid : pids) { try { Os.kill(pid, sig); } catch (ErrnoException ignored) {} }
    }

    private static boolean alive(int pid) { return new File("/proc/" + pid).exists(); }

    /** Завершити всі процеси запуску за міткою: спершу SIGTERM, а потім (через waitMs) SIGKILL. */
    void killTag(String runId, int waitMs) {
        List<Integer> pids = findTagged("KATE_RUN", runId);
        if (pids.isEmpty()) return;
        signal(pids, OsConstants.SIGTERM);
        long end = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < end) {
            boolean any = false;
            for (int p : pids) if (alive(p)) { any = true; break; }
            if (!any) return;
            try { Thread.sleep(60); } catch (InterruptedException e) { break; }
        }
        pids = findTagged("KATE_RUN", runId);
        signal(pids, OsConstants.SIGKILL);
    }

    /** Усе, що лишилось від попередніх сеансів: будь-який процес із KATE_RUN= чи KATE_APP_ID=. */
    private void sweepOrphans() {
        List<Integer> a = findTagged("KATE_RUN", null);
        List<Integer> b = findTagged("KATE_APP_ID", null);
        for (int p : b) if (!a.contains(p)) a.add(p);
        if (a.isEmpty()) return;
        System.err.println("Залишки попереднього сеансу: " + a.size() + " процесів — завершую");
        signal(a, OsConstants.SIGTERM);
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        List<Integer> left = findTagged("KATE_RUN", null);
        for (int p : findTagged("KATE_APP_ID", null)) if (!left.contains(p)) left.add(p);
        signal(left, OsConstants.SIGKILL);
        new File(tmpRoot, ".X11-unix/X" + X_DISPLAY).delete();
        new File(tmpRoot, ".X" + X_DISPLAY + "-lock").delete();
    }

    void killApp(final String app) {
        final List<Run> l = new ArrayList<>();
        synchronized (lock) { for (Run r : runs.values()) if (!r.service && r.app.equals(app) && r.state <= ST_RUN) l.add(r); }
        for (final Run r : l) {
            r.killedByUs = true;
            new Thread(new Runnable() { @Override public void run() { killTag(r.id, 1500); if (r.proc != null) r.proc.destroy(); } }).start();
        }
    }

    void killAll(final boolean includeServices) {
        final List<Run> l = new ArrayList<>();
        synchronized (lock) { for (Run r : runs.values()) if ((includeServices || !r.service) && r.state <= ST_RUN) l.add(r); }
        for (final Run r : l) {
            r.killedByUs = true;
            new Thread(new Runnable() { @Override public void run() { killTag(r.id, 1500); if (r.proc != null) r.proc.destroy(); } }).start();
        }
    }

    private void killAllNow() {
        List<Run> l;
        synchronized (lock) { l = new ArrayList<>(runs.values()); }
        for (Run r : l) { r.killedByUs = true; try { killTag(r.id, 700); } catch (Throwable ignored) {} if (r.proc != null) r.proc.destroy(); }
        sweepQuiet();
    }

    private void sweepQuiet() {
        List<Integer> a = findTagged("KATE_RUN", null);
        if (!a.isEmpty()) signal(a, OsConstants.SIGKILL);
    }

    // ------------------------------------------------------------------ відомості

    /** Повторно повідомити лаунчеру про всі запуски (після підключення). */
    void resend() {
        List<Run> l;
        synchronized (lock) { l = new ArrayList<>(runs.values()); }
        for (Run r : l) HostMain.cbRun(r.id, r.app, r.state, r.code, r.pid, r.service, r.name);
    }

    void markWindow(String tag) {
        if (tag == null || tag.isEmpty()) return;
        synchronized (lock) { for (Run r : runs.values()) if (r.app.equals(tag)) r.sawWindow = true; }
    }

    /** Хвіст журналу: "host", "x11" або "app:&lt;id&gt;". */
    String readLog(String name) {
        File f;
        if ("host".equals(name)) f = new File(dir, "host.log");
        else if ("x11".equals(name)) f = new File(logs, "x11.log");
        else if (name != null && name.startsWith("app:")) f = new File(logs, "app-" + safe(name.substring(4)) + ".log");
        else return "";
        if (!f.isFile()) return "(журналу ще немає)";
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            try {
                long len = raf.length(), from = Math.max(0, len - 40000);
                raf.seek(from);
                byte[] b = new byte[(int) (len - from)];
                raf.readFully(b);
                String s = new String(b, UTF8);
                return from > 0 ? "…\n" + s : s;
            } finally { raf.close(); }
        } catch (IOException e) { return "(не вдалося прочитати: " + e + ")"; }
    }

    // ------------------------------------------------------------------ допоміжне

    private static int pidOf(Process p) {
        try { return ((Number) Process.class.getMethod("pid").invoke(p)).intValue(); } catch (Throwable ignored) {}
        try { java.lang.reflect.Field f = p.getClass().getDeclaredField("pid"); f.setAccessible(true); return f.getInt(p); } catch (Throwable ignored) {}
        return 0;
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0 && bo.size() < max) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally { in.close(); }
    }

    private static void writeFile(File f, String s) throws IOException {
        FileOutputStream o = new FileOutputStream(f, false);
        try { o.write(s.getBytes(UTF8)); } finally { o.close(); }
    }

    private static void appendFile(File f, String s) throws IOException {
        if (f == null) return;
        FileOutputStream o = new FileOutputStream(f, true);
        try { o.write(s.getBytes(UTF8)); } finally { o.close(); }
    }

}
