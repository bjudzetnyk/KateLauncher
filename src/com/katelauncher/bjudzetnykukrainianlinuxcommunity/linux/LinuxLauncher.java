package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

import android.content.Context;
import android.widget.Toast;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppEntry;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.LinuxWm;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.WaylandSource;

/** Точка входу «запустити Linux-програму» для лаунчера (стіл, док, меню, пошук). */
public final class LinuxLauncher {
    private LinuxLauncher() {}

    public static final String FAKE = "@fake";

    public static void launch(Context c, AppEntry e) {
        LinuxApp app = LinuxStore.app(e.cls);
        if (app == null) return;
        LinuxWm.init(c);
        SessionState.init(c);
        if (app.command.trim().equals(FAKE)) { LinuxWm.get().spawnFake(app.key(), app.name); return; }
        LinuxEnv env = LinuxStore.env(app.envId);
        if (env == null) { Toast.makeText(c, I18n.t("lw_err_env"), Toast.LENGTH_LONG).show(); return; }
        WaylandSource.noteLaunch(app.id);
        int r = TermuxLauncher.run(c, env, app);
        if (r == TermuxLauncher.NO_TERMUX) Toast.makeText(c, I18n.t("lw_err_termux"), Toast.LENGTH_LONG).show();
        else if (r == TermuxLauncher.NO_PERMISSION) Toast.makeText(c, I18n.t("lw_err_perm"), Toast.LENGTH_LONG).show();
        else if (r == TermuxLauncher.FAILED) Toast.makeText(c, I18n.t("lw_err_failed"), Toast.LENGTH_LONG).show();
    }
}
