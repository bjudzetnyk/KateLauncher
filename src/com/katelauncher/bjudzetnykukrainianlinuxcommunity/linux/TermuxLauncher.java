package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.HostMain;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.Native;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.WaylandSource;

/**
 * Запуск Linux-програм. Два шляхи, один формат запиту:
 *  1) сеанс уже працює (хост у Termux підключений) — запит іде Binder-ом, без жодного запуску процесів;
 *  2) сеансу немає — через Termux RUN_COMMAND виконується короткий скрипт: кладе запит у чергу
 *     $TMPDIR/katewl/ctl, а якщо хоста нема — запускає його «на передньому плані». Саме цей процес і тримає
 *     Termux активним (сповіщення, wake-lock), доки живе сеанс.
 * Усе, що стосується самих програм (облік, мітки процесів, журнали, X11), робить хост (Session).
 * Команди користувача не розбираються й не змінюються: вони вставляються в скрипт дослівно.
 */
public final class TermuxLauncher {
    private TermuxLauncher() {}

    public static final String TERMUX_PKG = "com.termux";
    public static final String PERM = "com.termux.permission.RUN_COMMAND";
    public static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    public static final String SOCKET = "wayland-kate";
    public static final String X_DISPLAY = "42";

    public static final int OK = 0, NO_TERMUX = 1, NO_PERMISSION = 2, FAILED = 3;

    /** Екранування для одинарних лапок оболонки. */
    static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    /** Режим показу: програма > середовище > за замовчуванням. */
    public static String effectiveDisplay(LinuxEnv env, LinuxApp app) {
        String d = app != null ? app.display : "";
        if (d == null || d.isEmpty()) d = env != null ? env.display : "";
        if (d == null || d.isEmpty()) d = LinuxPrefs.defaultDisplay();
        return "x11".equals(d) ? "x11" : "wayland";
    }

    public static String newRunId() {
        return "r" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1679616), 36);
    }

    // ------------------------------------------------------------------ скрипти всередині середовища

    /** Прибирає застарілі замки профілів (Firefox/Chromium…), що лишились після аварійного завершення. */
    private static final String UNLOCK =
        "kate_unlock() {\n" +
        "  for pd in \"$HOME\"/.mozilla/firefox/*/ \"$HOME\"/.config/mozilla/firefox/*/ \"$HOME\"/snap/firefox/common/.mozilla/firefox/*/ \"$HOME\"/.librewolf/*/ \"$HOME\"/.waterfox/*/ \"$HOME\"/.thunderbird/*/; do\n" +
        "    l=\"${pd}lock\"; [ -L \"$l\" ] || continue\n" +
        "    t=$(readlink \"$l\" 2>/dev/null); p=${t##*+}\n" +
        "    case \"$p\" in ''|*[!0-9]*) rm -f \"$l\" \"${pd}.parentlock\" ;;\n" +
        "      *) if kill -0 \"$p\" 2>/dev/null && grep -qa -e firefox -e librewolf -e waterfox -e thunderbird \"/proc/$p/cmdline\" 2>/dev/null; then :; else rm -f \"$l\" \"${pd}.parentlock\"; fi ;;\n" +
        "    esac\n" +
        "  done\n" +
        "  for l in \"$HOME\"/.config/*/SingletonLock \"$HOME\"/.config/*/*/SingletonLock; do\n" +
        "    [ -L \"$l\" ] || continue\n" +
        "    t=$(readlink \"$l\" 2>/dev/null); p=${t##*-}\n" +
        "    case \"$p\" in ''|*[!0-9]*) ok=0 ;; *) kill -0 \"$p\" 2>/dev/null && ok=1 || ok=0 ;; esac\n" +
        "    [ \"$ok\" = 1 ] || rm -f \"$l\" \"${l%/*}/SingletonSocket\" \"${l%/*}/SingletonCookie\"\n" +
        "  done\n" +
        "}\nkate_unlock\n";

    private static String findWayland() {
        return "for d in /tmp/katewl " + q(TERMUX_PREFIX + "/tmp/katewl") + "; do\n" +
               "  if [ -S \"$d/" + SOCKET + "\" ]; then XDG_RUNTIME_DIR=\"$d\"; break; fi\n" +
               "done\n";
    }

    /** Те, що виконується всередині середовища (після команди входу, у «sh -c»). */
    static String guestScript(LinuxApp app, String runId, String display) {
        StringBuilder pl = new StringBuilder();
        pl.append("KATE_APP_ID=").append(q(app.id)).append("; KATE_RUN=").append(q(runId)).append("; export KATE_APP_ID KATE_RUN\n");
        pl.append(UNLOCK);
        if ("x11".equals(display)) {
            pl.append("[ -S /tmp/.X11-unix/X").append(X_DISPLAY).append(" ] || echo 'KateLauncher: X-сокет не видно в середовищі (для proot-distro додайте --shared-tmp)' >&2\n");
            pl.append("export DISPLAY=:").append(X_DISPLAY).append(" XDG_SESSION_TYPE=x11 GDK_BACKEND=x11 QT_QPA_PLATFORM=xcb MOZ_ENABLE_WAYLAND=0\n");
            pl.append("unset WAYLAND_DISPLAY WAYLAND_SOCKET\n");
            pl.append("export ELECTRON_OZONE_PLATFORM_HINT=x11 ELECTRON_DISABLE_SANDBOX=1 SDL_VIDEODRIVER=x11 CLUTTER_BACKEND=x11\n");
            pl.append("export _JAVA_AWT_WM_NONREPARENTING=1 GTK_USE_PORTAL=0 NO_AT_BRIDGE=1 LIBGL_ALWAYS_SOFTWARE=1\n");
            pl.append("if [ -z \"$XDG_RUNTIME_DIR\" ]; then XDG_RUNTIME_DIR=\"/tmp/kate-xdg-$(id -u)\"; mkdir -p \"$XDG_RUNTIME_DIR\"; chmod 700 \"$XDG_RUNTIME_DIR\" 2>/dev/null; export XDG_RUNTIME_DIR; fi\n");
        } else {
            pl.append(findWayland());
            pl.append("[ -z \"$XDG_RUNTIME_DIR\" ] && echo 'KateLauncher: Wayland-сокет не видно в середовищі (для proot-distro додайте --shared-tmp)' >&2\n");
            pl.append("export XDG_RUNTIME_DIR WAYLAND_DISPLAY=").append(SOCKET).append(" XDG_SESSION_TYPE=wayland")
              .append(" GDK_BACKEND=wayland,x11 QT_QPA_PLATFORM='wayland;xcb' MOZ_ENABLE_WAYLAND=1\n");
            pl.append("unset DISPLAY WAYLAND_SOCKET\n");   // щоб програми не обирали X11, якого тут немає
            pl.append("export ELECTRON_OZONE_PLATFORM_HINT=wayland ELECTRON_DISABLE_SANDBOX=1 SDL_VIDEODRIVER=wayland CLUTTER_BACKEND=wayland")
              .append(" _JAVA_AWT_WM_NONREPARENTING=1 GTK_USE_PORTAL=0 NO_AT_BRIDGE=1 LIBGL_ALWAYS_SOFTWARE=1\n");
        }
        pl.append(app.command).append("\n");   // дослівно
        return pl.toString();
    }

    /** Служба Xwayland: працює у середовищі першої X11-програми, решта підключається до неї через спільний /tmp. */
    static String xwaylandScript() {
        StringBuilder pl = new StringBuilder();
        pl.append("KATE_APP_ID='@xwayland'; KATE_RUN='xwl'; KATE_XWL=1; export KATE_APP_ID KATE_RUN KATE_XWL\n");
        pl.append(findWayland());
        pl.append("[ -z \"$XDG_RUNTIME_DIR\" ] && { echo 'KateLauncher: Wayland-сокет не видно в середовищі (для proot-distro додайте --shared-tmp)' >&2; exit 126; }\n");
        pl.append("export XDG_RUNTIME_DIR WAYLAND_DISPLAY=").append(SOCKET).append("\nunset DISPLAY\n");
        pl.append("command -v Xwayland >/dev/null 2>&1 || { echo 'KateLauncher: у середовищі немає Xwayland. Встановіть: apt install xwayland' >&2; exit 127; }\n");
        pl.append("mkdir -p /tmp/.X11-unix; chmod 1777 /tmp/.X11-unix 2>/dev/null\n");
        pl.append("rm -f /tmp/.X11-unix/X").append(X_DISPLAY).append(" /tmp/.X").append(X_DISPLAY).append("-lock\n");
        pl.append("SHM=''; Xwayland -help 2>&1 | grep -q -e '-shm' && SHM=-shm\n");
        pl.append("echo \"Xwayland: $(command -v Xwayland) $SHM\" >&2\n");
        pl.append("exec Xwayland :").append(X_DISPLAY).append(" -rootless -ac $SHM -noreset -nolisten tcp\n");
        return pl.toString();
    }

    /** Текст запиту для менеджера сеансу (той самий для Binder і для файлової черги). */
    public static String request(LinuxEnv env, LinuxApp app, String display, String runId) {
        String pre = env.command.isEmpty() ? "" : env.command + " ";
        StringBuilder r = new StringBuilder();
        String name;
        try { name = java.net.URLEncoder.encode(app.name == null ? "" : app.name, "UTF-8"); } catch (Exception e) { name = ""; }
        r.append("#KATE-REQ 2\nrun=").append(runId).append("\napp=").append(app.id).append("\nname=").append(name).append("\ndisplay=").append(display).append("\n");
        if ("x11".equals(display)) {
            r.append("#KATE-SEC xwl\n").append(pre).append("sh -c ").append(q(xwaylandScript())).append("\n");
        }
        r.append("#KATE-SEC app\n");
        if ("x11".equals(display)) {
            r.append("i=0; while [ ! -e \"$KATE_DIR/xwm.ready\" ] && [ $i -lt 400 ]; do sleep 0.1; i=$((i+1)); done\n");
        }
        r.append(pre).append("sh -c ").append(q(guestScript(app, runId, display))).append("\n");
        return r.toString();
    }

    // ------------------------------------------------------------------ скрипт для Termux

    /** Скрипт для RUN_COMMAND (sh). Видимий для тестів/діагностики. */
    public static String script(Context ctx, String request, String runId) {
        return script(ctx.getApplicationInfo().sourceDir, ctx.getApplicationInfo().nativeLibraryDir, ctx.getPackageName(), LinuxPrefs.token(), request, runId);
    }

    /** Чиста функція (без Android) — зручно перевіряти скрипт на звичайному sh. */
    public static String script(String apk, String lib, String pkg, String token, String request, String runId) {
        String eof = "KATE_EOF_" + Long.toString(Math.abs(System.nanoTime()), 36) + Integer.toString((int) (Math.random() * 46656), 36);
        String sig = apk + ":" + new java.io.File(apk).lastModified() + ":" + wlVersion();
        StringBuilder s = new StringBuilder();
        s.append("D=\"${TMPDIR:-").append(TERMUX_PREFIX).append("/tmp}/katewl\"\n");
        s.append("mkdir -p \"$D/ctl\" \"$D/logs\" \"$D/run\" 2>/dev/null; chmod 700 \"$D\" 2>/dev/null\n");
        s.append("SIG=").append(q(sig)).append("\n");
        // 1) запит у чергу (атомарно: tmp -> req)
        s.append("R=\"$D/ctl/").append(runId).append("\"\n");
        s.append("cat > \"$R.tmp\" <<'").append(eof).append("'\n").append(request.endsWith("\n") ? request : request + "\n").append(eof).append("\n");
        s.append("mv \"$R.tmp\" \"$R.req\"\n");
        // 2) хост живий (і того ж складання) — запит підхопить він
        s.append("alive() { [ -f \"$D/host.pid\" ] && kill -0 \"$(cat \"$D/host.pid\" 2>/dev/null)\" 2>/dev/null && [ -S \"$D/").append(SOCKET)
         .append("\" ] && [ \"$(cat \"$D/host.sig\" 2>/dev/null)\" = \"$SIG\" ]; }\n");
        s.append("alive && exit 0\n");
        // 3) хоста немає: стаємо тим, хто його запускає (замок проти подвійного старту)
        s.append("if ! mkdir \"$D/starting.lock\" 2>/dev/null; then\n");
        s.append("  if [ -n \"$(find \"$D/starting.lock\" -mmin -1 2>/dev/null)\" ]; then exit 0; fi\n");
        s.append("  rm -rf \"$D/starting.lock\"; mkdir \"$D/starting.lock\" 2>/dev/null || exit 0\n");
        s.append("fi\n");
        s.append("trap 'rmdir \"$D/starting.lock\" 2>/dev/null; command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock' EXIT\n");
        s.append("[ -f \"$D/host.pid\" ] && kill -9 \"$(cat \"$D/host.pid\" 2>/dev/null)\" 2>/dev/null\n");
        s.append("rm -f \"$D/").append(SOCKET).append("\" \"$D/host.pid\" \"$D/xwm.ready\"\n");
        s.append("printf '%s' \"$SIG\" > \"$D/host.sig\"\n");
        s.append("command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock\n");
        s.append("( i=0; while [ ! -S \"$D/").append(SOCKET).append("\" ] && [ $i -lt 150 ]; do sleep 0.1; i=$((i+1)); done; rmdir \"$D/starting.lock\" 2>/dev/null ) &\n");
        s.append("export KATE_ORIG_LD_PRELOAD=\"$LD_PRELOAD\" KATE_ORIG_LD_LIBRARY_PATH=\"$LD_LIBRARY_PATH\"\n");
        s.append("( unset LD_PRELOAD; export CLASSPATH=").append(q(apk)).append(" LD_LIBRARY_PATH=").append(q(lib))
         .append(" KATE_LIB=").append(q(lib)).append(" KATE_DIR=\"$D\" KATE_PKG=").append(q(pkg)).append(" KATE_TOKEN=").append(q(token)).append("\n");
        s.append("  exec /system/bin/app_process / ").append(HostMain.class.getName()).append(" ) > \"$D/host.log\" 2>&1 < /dev/null\n");
        s.append("exit $?\n");
        return s.toString();
    }

    private static int wlVersion() { return com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.Proto.VERSION; }

    /** Аварійне прибирання без хоста: усе, що несе мітки KATE_RUN / KATE_APP_ID. */
    public static String killAllScript() {
        return "D=\"${TMPDIR:-" + TERMUX_PREFIX + "/tmp}/katewl\"\n" +
               "[ -f \"$D/host.pid\" ] && kill -9 \"$(cat \"$D/host.pid\" 2>/dev/null)\" 2>/dev/null\n" +
               "me=$$\n" +
               "for p in /proc/[0-9]*; do\n" +
               "  n=${p#/proc/}; [ \"$n\" = \"$me\" ] && continue\n" +
               "  [ -r \"$p/environ\" ] || continue\n" +
               "  if tr '\\0' '\\n' < \"$p/environ\" 2>/dev/null | grep -q -e '^KATE_RUN=' -e '^KATE_APP_ID='; then kill -9 \"$n\" 2>/dev/null; fi\n" +
               "done\n" +
               "rm -f \"$D/" + SOCKET + "\" \"$D/host.pid\" \"$D/xwm.ready\" \"$D\"/ctl/* \"${TMPDIR:-" + TERMUX_PREFIX + "/tmp}/.X11-unix/X" + X_DISPLAY + "\" \"${TMPDIR:-" + TERMUX_PREFIX + "/tmp}/.X" + X_DISPLAY + "-lock\"\n" +
               "rmdir \"$D/starting.lock\" 2>/dev/null\n" +
               "command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock\n";
    }

    // ------------------------------------------------------------------ запуск

    public static int run(Context ctx, LinuxEnv env, LinuxApp app) {
        String display = effectiveDisplay(env, app);
        String runId = newRunId();
        String req = request(env, app, display, runId);
        WaylandSource src = WaylandSource.get();
        if (src.isConnected()) {            // сеанс працює: найшвидший шлях, без Termux
            src.sessionCmd(Native.SES_LAUNCH, 0, req);
            return OK;
        }
        return runTermux(ctx, script(ctx, req, runId));
    }

    /** Вбити все, що лишилось від Linux-програм, навіть коли хост уже не відповідає. */
    public static int killEverything(Context ctx) {
        WaylandSource src = WaylandSource.get();
        if (src.isConnected()) src.sessionCmd(Native.SES_STOP, 0, null);
        return runTermux(ctx, killAllScript());
    }

    private static int runTermux(Context ctx, String sh) {
        try { ctx.getPackageManager().getPackageInfo(TERMUX_PKG, 0); } catch (PackageManager.NameNotFoundException e) { return NO_TERMUX; }
        if (ctx.checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) {
            if (ctx instanceof Activity) ((Activity) ctx).requestPermissions(new String[]{PERM}, 77);
            return NO_PERMISSION;
        }
        Intent i = new Intent("com.termux.RUN_COMMAND");
        i.setClassName(TERMUX_PKG, "com.termux.app.RunCommandService");
        i.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_PREFIX + "/bin/sh");
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", sh});
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            try { ctx.startService(i); } catch (IllegalStateException e) { ctx.startForegroundService(i); }
            return OK;
        } catch (SecurityException e) {
            return NO_PERMISSION;
        } catch (Exception e) {
            return FAILED;
        }
    }
}
