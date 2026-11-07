package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.text.Html;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppRepository;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.IconCache;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxApp;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxEnv;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxIcons;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxPrefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.SessionState;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.TermuxLauncher;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.Native;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.WaylandSource;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.LinuxWm;

/** Розділ «Linux-середовища»: інструкція, середовища, програми, вікна й продуктивність. Без майстрів і автоперевірок. */
final class LinuxPanel {
    private LinuxPanel() {}

    static void build(final SettingsActivity a, LinearLayout out) {
        TextView t = Ui.text(a, I18n.t("panel_linux"), 19, Theme.ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(a, 6), Ui.dp(a, 4), 0, 0);
        out.addView(t);

        setup(a, out);
        sessionBlock(a, out);
        addEnvBlock(a, out);
        envsBlock(a, out);
        appsBlock(a, out);
        windowsBlock(a, out);
        layoutsBlock(a, out);
    }

    // ---------------- Інструкція ----------------

    private static void setup(Activity a, LinearLayout out) {
        TextView body = new TextView(a);
        body.setTextColor(SettingsUi.TEXT);
        body.setTextSize(14.5f);
        body.setLineSpacing(Ui.dp(a, 3), 1f);
        body.setTextIsSelectable(true);
        body.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        body.setText(Html.fromHtml(I18n.t("lx_setup_text").replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY));
        LinearLayout g = SettingsUi.group(a);
        g.addView(body);
        out.addView(SettingsUi.groupTitle(a, I18n.t("lx_setup_title")));
        out.addView(g);
    }

    // ---------------- Поля й рядки ----------------

    /** Поле вводу: без автовиправлення й підказок — введене не змінюється. */
    static EditText field(Context c, String hint, boolean multi) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextColor(SettingsUi.TEXT);
        e.setHintTextColor(SettingsUi.TEXT_DIM);
        e.setTextSize(15);
        Ui.tintEdit(e);
        int type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (multi) { type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE; e.setMinLines(2); e.setMaxLines(8); e.setTypeface(Typeface.MONOSPACE); e.setGravity(Gravity.TOP); }
        e.setInputType(type);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        return e;
    }

    private static View pad(Context c, View v, int l, int t, int r, int b) {
        LinearLayout w = new LinearLayout(c);
        w.setPadding(Ui.dp(c, l), Ui.dp(c, t), Ui.dp(c, r), Ui.dp(c, b));
        w.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return w;
    }

    private static TextView button(Context c, String label, int color, final Runnable r) {
        TextView b = Ui.text(c, label, 15, color);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        b.setBackground(Ui.pressable(c, 0x22FFFFFF, 0x44FFFFFF, 10));
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return b;
    }

    private static View itemRow(final Context c, String title, String sub, final Runnable edit, final Runnable del) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(c, title, 15, SettingsUi.TEXT));
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(c, sub, 12.5f, SettingsUi.TEXT_DIM);
            s.setTypeface(Typeface.MONOSPACE); s.setMaxLines(2); s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(s);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView e = Ui.text(c, I18n.t("lx_edit"), 14, Theme.ACCENT);
        e.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        e.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { edit.run(); } });
        TextView d = Ui.text(c, I18n.t("lx_delete"), 14, 0xFFFF4D4D);
        d.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        d.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { del.run(); } });
        row.addView(e); row.addView(d);
        return row;
    }

    private static void confirm(Activity a, String msg, final Runnable yes) {
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setMessage(msg)
                .setPositiveButton(I18n.t("lx_delete"), new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface d, int w) { yes.run(); } })
                .setNegativeButton(I18n.t("cancel"), null).show();
    }

    private static void changed() { AppRepository.syncLinux(); IconCache.clear(); com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.FolderIcon.clear(); }


    // ---------------- Режим показу (Wayland / X11) ----------------

    /** 0 — успадкувати, 1 — Wayland, 2 — X11. */
    private static int displayIndex(String d) { return "wayland".equals(d) ? 1 : "x11".equals(d) ? 2 : 0; }
    private static String displayValue(int i) { return i == 1 ? "wayland" : i == 2 ? "x11" : ""; }

    private static TextView displayChooser(final Activity a, final String label, final boolean forApp, final int[] sel) {
        final String[] labels = { I18n.t(forApp ? "lx_display_as_env" : "lx_display_as_default"), I18n.t("lx_display_wayland"), I18n.t("lx_display_x11") };
        final TextView v = Ui.text(a, label + ":  " + labels[sel[0]] + "  \u25BE", 15, Theme.ACCENT);
        v.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                SettingsUi.choosePopup(a, v, labels, sel[0], new SettingsUi.OnInt() {
                    @Override public void on(int i) { sel[0] = i; v.setText(label + ":  " + labels[i] + "  \u25BE"); }
                });
            }
        });
        return v;
    }

    // ---------------- Сеанс і запуск ----------------

    private static String stateText(SessionState.RunInfo r) {
        switch (r.state) {
            case SessionState.ST_START: return I18n.t("lx_st_start");
            case SessionState.ST_RUN: return I18n.t("lx_st_run");
            case SessionState.ST_KILLED: return I18n.t("lx_st_killed");
            default: return I18n.t("lx_st_exit").replace("%1", String.valueOf(r.code));
        }
    }

    private static void sessionBlock(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("lx_sess_title")));
        LinearLayout g = SettingsUi.group(a);

        final TextView status = Ui.text(a, "", 14.5f, SettingsUi.TEXT);
        status.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 6));
        g.addView(status);
        final LinearLayout running = new LinearLayout(a);
        running.setOrientation(LinearLayout.VERTICAL);
        g.addView(running);

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                boolean on = SessionState.hostAlive();
                status.setText(on ? I18n.t("lx_sess_on").replace("%1", String.valueOf(SessionState.activeCount())) : I18n.t("lx_sess_off"));
                status.setTextColor(on ? SettingsUi.TEXT : SettingsUi.TEXT_DIM);
                running.removeAllViews();
                int shown = 0;
                for (final SessionState.RunInfo r : SessionState.list()) {
                    if (r.service) continue;
                    shown++;
                    LinearLayout row = new LinearLayout(a);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
                    LinearLayout col = new LinearLayout(a);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.addView(Ui.text(a, r.name, 15, SettingsUi.TEXT));
                    col.addView(Ui.text(a, stateText(r) + (r.pid > 0 && r.state <= SessionState.ST_RUN ? "  \u00B7  pid " + r.pid : ""), 12.5f, SettingsUi.TEXT_DIM));
                    row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    if (r.state <= SessionState.ST_RUN) {
                        TextView stop = Ui.text(a, I18n.t("lx_stop"), 14, 0xFFFF4D4D);
                        stop.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
                        stop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { WaylandSource.get().sessionCmd(Native.SES_KILL_APP, 0, r.app); } });
                        row.addView(stop);
                    }
                    running.addView(row);
                }
                if (on && shown == 0) running.addView(pad(a, Ui.text(a, I18n.t("lx_running_none"), 13, SettingsUi.TEXT_DIM), 16, 0, 16, 6));
            }
        };
        refresh.run();
        final SessionState.Listener ls = new SessionState.Listener() {
            @Override public void onRuns() { refresh.run(); }
            @Override public void onLog(String name, String text) {}
        };
        SessionState.addListener(ls);
        g.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { SessionState.addListener(ls); refresh.run(); }
            @Override public void onViewDetachedFromWindow(View v) { SessionState.removeListener(ls); }
        });
        WaylandSource.get().sessionCmd(Native.SES_LIST, 0, null);

        SettingsUi.divider(a, g);
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("lx_display_default"), new String[]{I18n.t("lx_display_wayland"), I18n.t("lx_display_x11")},
                "x11".equals(LinuxPrefs.defaultDisplay()) ? 1 : 0,
                new SettingsUi.OnInt() { @Override public void on(int v) { Prefs.putString(LinuxPrefs.DISPLAY, v == 1 ? "x11" : "wayland"); } }));
        g.addView(pad(a, Ui.text(a, I18n.t("lx_display_hint"), 12.5f, SettingsUi.TEXT_DIM), 16, 0, 16, 8));
        SettingsUi.add(a, g, SettingsUi.sliderRow(a, I18n.t("lx_idle"), 0, 60, LinuxPrefs.idleMinutes(),
                new SettingsUi.Fmt() { @Override public String f(int v) { return v == 0 ? I18n.t("lx_idle_off") : I18n.t("lx_idle_fmt").replace("%1", String.valueOf(v)); } },
                new SettingsUi.OnInt() { @Override public void on(int v) { Prefs.putInt(LinuxPrefs.IDLE_MIN, v); WaylandSource.get().sessionCmd(Native.SES_IDLE, v, null); } }));
        SettingsUi.add(a, g, SettingsUi.switchRow(a, I18n.t("lx_debug"), LinuxPrefs.debug(), new SettingsUi.OnBool() {
            @Override public void on(boolean v) { Prefs.putBool(LinuxPrefs.DEBUG, v); WaylandSource.get().sessionCmd(Native.SES_DEBUG, v ? 1 : 0, null); }
        }));
        SettingsUi.divider(a, g);
        LinearLayout btns = new LinearLayout(a);
        btns.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 12));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp.leftMargin = bp.rightMargin = Ui.dp(a, 4);
        btns.addView(button(a, I18n.t("lx_logs"), Theme.ACCENT, new Runnable() { @Override public void run() { logsDialog(a); } }), bp);
        btns.addView(button(a, I18n.t("lx_stop_all"), SettingsUi.TEXT, new Runnable() {
            @Override public void run() { WaylandSource.get().sessionCmd(Native.SES_KILL_ALL, 0, null); }
        }), bp);
        g.addView(btns);
        g.addView(pad(a, button(a, I18n.t("lx_reset_session"), 0xFFFF4D4D, new Runnable() {
            @Override public void run() {
                new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setMessage(I18n.t("lx_reset_confirm"))
                        .setPositiveButton(I18n.t("lx_reset_session"), new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                int r = TermuxLauncher.killEverything(a);
                                Toast.makeText(a, r == TermuxLauncher.OK ? I18n.t("lx_reset_sent") : I18n.t(r == TermuxLauncher.NO_TERMUX ? "lw_err_termux" : r == TermuxLauncher.NO_PERMISSION ? "lw_err_perm" : "lw_err_failed"), Toast.LENGTH_LONG).show();
                            }
                        }).setNegativeButton(I18n.t("cancel"), null).show();
            }
        }), 12, 0, 12, 12));
        out.addView(g);
    }

    private static void logsDialog(final SettingsActivity a) {
        if (!WaylandSource.get().isConnected()) { Toast.makeText(a, I18n.t("lx_no_session"), Toast.LENGTH_LONG).show(); return; }
        final List<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        keys.add("host"); labels.add(I18n.t("lx_log_session"));
        keys.add("x11"); labels.add(I18n.t("lx_log_x11"));
        for (LinuxApp app : LinuxStore.apps()) { keys.add("app:" + app.id); labels.add(I18n.t("lx_log_app").replace("%1", app.name)); }
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(I18n.t("lx_logs"))
                .setItems(labels.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int i) { showLog(a, keys.get(i), labels.get(i)); }
                }).setNegativeButton(I18n.t("lx_log_close"), null).show();
    }

    private static void showLog(final Activity a, final String key, String title) {
        final TextView tv = new TextView(a);
        tv.setTypeface(Typeface.MONOSPACE); tv.setTextSize(11.5f); tv.setTextColor(SettingsUi.TEXT);
        tv.setTextIsSelectable(true);
        tv.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        tv.setText(I18n.t("lx_log_loading"));
        final android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(tv);
        final SessionState.Listener ls = new SessionState.Listener() {
            @Override public void onRuns() {}
            @Override public void onLog(String name, String text) {
                if (!key.equals(name)) return;
                tv.setText(text);
                sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } });
            }
        };
        SessionState.addListener(ls);
        AlertDialog d = new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(title).setView(sv)
                .setPositiveButton(I18n.t("lx_log_copy"), null)
                .setNegativeButton(I18n.t("lx_log_close"), null).create();
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface x) { SessionState.removeListener(ls); }
        });
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("log", tv.getText()));
                Toast.makeText(a, I18n.t("lx_log_copied"), Toast.LENGTH_SHORT).show();
            }
        });
        WaylandSource.get().sessionCmd(Native.SES_GET_LOG, 0, key);
    }

    // ---------------- Додати середовище ----------------

    private static void addEnvBlock(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("lx_add_env_title")));
        LinearLayout g = SettingsUi.group(a);
        final EditText name = field(a, I18n.t("lx_env_name"), false);
        final EditText cmd = field(a, I18n.t("lx_env_cmd_hint"), false);
        cmd.setTypeface(Typeface.MONOSPACE);
        g.addView(pad(a, name, 16, 8, 16, 0));
        g.addView(pad(a, cmd, 16, 4, 16, 4));
        View add = button(a, I18n.t("lx_add"), Theme.ACCENT, new Runnable() {
            @Override public void run() {
                String n = name.getText().toString().trim(), c = cmd.getText().toString();
                if (n.isEmpty()) { Toast.makeText(a, I18n.t("lx_need_fields"), Toast.LENGTH_SHORT).show(); return; }
                LinuxStore.addEnv(n, c);
                a.rebuild();
            }
        });
        g.addView(pad(a, add, 16, 4, 16, 12));
        out.addView(g);
    }

    // ---------------- Середовища ----------------

    private static void envsBlock(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("lx_envs_title")));
        LinearLayout g = SettingsUi.group(a);
        List<LinuxEnv> envs = LinuxStore.envs();
        if (envs.isEmpty()) g.addView(pad(a, Ui.text(a, I18n.t("lx_envs_empty"), 14, SettingsUi.TEXT_DIM), 16, 14, 16, 14));
        for (final LinuxEnv e : envs) {
            SettingsUi.add(a, g, itemRow(a, e.name, e.command.isEmpty() ? "—" : e.command,
                    new Runnable() { @Override public void run() { envDialog(a, e); } },
                    new Runnable() { @Override public void run() {
                        confirm(a, I18n.t("lx_confirm_env"), new Runnable() { @Override public void run() { LinuxStore.deleteEnv(a, e.id); changed(); a.rebuild(); } });
                    } }));
        }
        out.addView(g);
    }

    private static void envDialog(final SettingsActivity a, final LinuxEnv e) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 20), 0);
        final EditText name = field(a, I18n.t("lx_env_name"), false);
        final EditText cmd = field(a, I18n.t("lx_env_cmd_hint"), false);
        cmd.setTypeface(Typeface.MONOSPACE);
        name.setText(e.name); cmd.setText(e.command);
        box.addView(name); box.addView(cmd);
        final int[] disp = { displayIndex(e.display) };
        final TextView dv = displayChooser(a, I18n.t("lx_env_display"), false, disp);
        box.addView(dv);
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(I18n.t("lx_edit")).setView(box)
                .setPositiveButton(I18n.t("lx_save"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        if (n.isEmpty()) return;
                        LinuxStore.updateEnv(e.id, n, cmd.getText().toString(), displayValue(disp[0]));
                        a.rebuild();
                    }
                }).setNegativeButton(I18n.t("cancel"), null).show();
    }

    // ---------------- Програми ----------------

    private static void appsBlock(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("lx_apps_title")));
        LinearLayout g = SettingsUi.group(a);

        // Розгортний пункт «Як додати?»
        final TextView how = new TextView(a);
        how.setTextColor(SettingsUi.TEXT_DIM); how.setTextSize(14);
        how.setLineSpacing(Ui.dp(a, 2), 1f);
        how.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 12));
        how.setText(Html.fromHtml(I18n.t("lx_howto_text").replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY));
        how.setVisibility(View.GONE);
        final TextView head = Ui.text(a, "\u25B8  " + I18n.t("lx_howto_title"), 15, Theme.ACCENT);
        head.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 12));
        head.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean open = how.getVisibility() == View.VISIBLE;
                how.setVisibility(open ? View.GONE : View.VISIBLE);
                head.setText((open ? "\u25B8  " : "\u25BE  ") + I18n.t("lx_howto_title"));
            }
        });
        g.addView(head); g.addView(how);

        List<LinuxApp> apps = LinuxStore.apps();
        if (apps.isEmpty()) { SettingsUi.divider(a, g); g.addView(pad(a, Ui.text(a, I18n.t("lx_apps_empty"), 14, SettingsUi.TEXT_DIM), 16, 14, 16, 14)); }
        for (final LinuxApp app : apps) {
            LinuxEnv env = LinuxStore.env(app.envId);
            String sub = (env != null ? env.name : "?") + "  \u00B7  " + app.command;
            SettingsUi.add(a, g, itemRow(a, app.name, sub,
                    new Runnable() { @Override public void run() { appDialog(a, app); } },
                    new Runnable() { @Override public void run() {
                        confirm(a, I18n.t("lx_confirm_app"), new Runnable() { @Override public void run() { LinuxStore.deleteApp(a, app.id); changed(); a.rebuild(); } });
                    } }));
        }
        SettingsUi.divider(a, g);
        g.addView(pad(a, button(a, "+ " + I18n.t("lx_add_app"), Theme.ACCENT, new Runnable() { @Override public void run() { appDialog(a, null); } }), 16, 10, 16, 12));
        out.addView(g);
    }

    /** Додавання/редагування програми. Усе вручну; команда зберігається дослівно. */
    private static void appDialog(final SettingsActivity a, final LinuxApp existing) {
        final List<LinuxEnv> envs = LinuxStore.envs();
        if (envs.isEmpty()) { Toast.makeText(a, I18n.t("lx_need_env"), Toast.LENGTH_LONG).show(); return; }
        final String id = existing != null ? existing.id : LinuxStore.newId("l");
        final boolean[] custom = { existing != null && existing.customIcon };
        final boolean[] saved = { false };
        final int[] envIdx = { 0 };
        if (existing != null) for (int i = 0; i < envs.size(); i++) if (envs.get(i).id.equals(existing.envId)) envIdx[0] = i;

        ScrollLinear box = new ScrollLinear(a);
        final EditText name = field(a, I18n.t("lx_app_name"), false);
        final EditText cmd = field(a, I18n.t("lx_app_cmd_hint"), true);
        if (existing != null) { name.setText(existing.name); cmd.setText(existing.command); }
        box.col.addView(name);

        final TextView envView = Ui.text(a, I18n.t("lx_app_env") + ":  " + envs.get(envIdx[0]).name + "  \u25BE", 15, Theme.ACCENT);
        envView.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 10));
        envView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] labels = new String[envs.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = envs.get(i).name;
                SettingsUi.choosePopup(a, envView, labels, envIdx[0], new SettingsUi.OnInt() {
                    @Override public void on(int i) { envIdx[0] = i; envView.setText(I18n.t("lx_app_env") + ":  " + envs.get(i).name + "  \u25BE"); }
                });
            }
        });
        box.col.addView(envView);
        final int[] disp = { displayIndex(existing != null ? existing.display : "") };
        box.col.addView(displayChooser(a, I18n.t("lx_display_label"), true, disp));
        box.col.addView(cmd);

        // Іконка
        LinearLayout iconRow = new LinearLayout(a);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        iconRow.setPadding(0, Ui.dp(a, 14), 0, 0);
        final ImageView preview = new ImageView(a);
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                LinuxApp tmp = new LinuxApp(id, "", "", "", custom[0]);
                Bitmap b = LinuxIcons.load(a, tmp, Ui.dp(a, 56));
                preview.setImageBitmap(b);
            }
        };
        refresh.run();
        iconRow.addView(preview, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.leftMargin = Ui.dp(a, 12);
        iconRow.addView(button(a, I18n.t("lx_icon_gallery"), SettingsUi.TEXT, new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                a.startForResult(i, new SettingsActivity.ResultHandler() {
                    @Override public void onResult(int res, Intent data) {
                        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
                        if (LinuxIcons.saveFromUri(a, id, data.getData())) { custom[0] = true; refresh.run(); }
                    }
                });
            }
        }), bl);
        iconRow.addView(button(a, I18n.t("lx_icon_default"), SettingsUi.TEXT, new Runnable() {
            @Override public void run() { custom[0] = false; LinuxIcons.delete(a, id); refresh.run(); }
        }), bl);
        TextView il = Ui.text(a, I18n.t("lx_app_icon"), 13, SettingsUi.TEXT_DIM);
        il.setPadding(0, Ui.dp(a, 12), 0, 0);
        box.col.addView(il);
        box.col.addView(iconRow);

        final AlertDialog d = new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(existing == null ? I18n.t("lx_add_app") : I18n.t("lx_edit")).setView(box)
                .setPositiveButton(I18n.t("lx_save"), null)
                .setNegativeButton(I18n.t("cancel"), null).create();
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface x) {
                if (!saved[0] && existing == null) LinuxIcons.delete(a, id);   // скасовано: прибрати вибрану іконку
            }
        });
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String n = name.getText().toString().trim(), c = cmd.getText().toString();
                if (n.isEmpty() || c.trim().isEmpty()) { Toast.makeText(a, I18n.t("lx_need_fields"), Toast.LENGTH_SHORT).show(); return; }
                // c зберігається як є: без trim, без «чистки»
                LinuxApp na = new LinuxApp(id, n, envs.get(envIdx[0]).id, c, custom[0]);
                na.display = displayValue(disp[0]);
                LinuxStore.putApp(na);
                saved[0] = true;
                changed();
                d.dismiss();
                a.rebuild();
            }
        });
    }

    private static final class ScrollLinear extends android.widget.ScrollView {
        final LinearLayout col;
        ScrollLinear(Context c) {
            super(c);
            col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 4));
            addView(col);
        }
    }

    // ---------------- Вікна й продуктивність ----------------

    private static void windowsBlock(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("lx_windows_title")));
        LinearLayout g = SettingsUi.group(a);
        SettingsUi.add(a, g, SettingsUi.switchRow(a, I18n.t("lx_overlay"), LinuxPrefs.overlayAll(), new SettingsUi.OnBool() {
            @Override public void on(boolean v) {
                if (v && !Settings.canDrawOverlays(a)) {
                    Toast.makeText(a, I18n.t("lx_overlay_perm"), Toast.LENGTH_LONG).show();
                    try { a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName()))); } catch (Exception ignored) {}
                }
                Prefs.putBool(LinuxPrefs.OVERLAY, v);
                LinuxWm.get().reloadPrefs();
            }
        }));
        SettingsUi.add(a, g, SettingsUi.sliderRow(a, I18n.t("lx_fps"), 5, 120, Prefs.getInt(LinuxPrefs.FPS, 30),
                new SettingsUi.Fmt() { @Override public String f(int v) { return v + " FPS"; } },
                new SettingsUi.OnInt() { @Override public void on(int v) { Prefs.putInt(LinuxPrefs.FPS, v); LinuxWm.get().reloadPrefs(); } }));
        SettingsUi.add(a, g, SettingsUi.switchRow(a, I18n.t("lx_saver"), LinuxPrefs.saver(), new SettingsUi.OnBool() {
            @Override public void on(boolean v) { Prefs.putBool(LinuxPrefs.SAVER, v); LinuxWm.get().reloadPrefs(); }
        }));
        SettingsUi.add(a, g, SettingsUi.sliderRow(a, I18n.t("lx_zoom"), 50, 300, LinuxPrefs.zoomPercent(a),
                new SettingsUi.Fmt() { @Override public String f(int v) { return v + "%"; } },
                new SettingsUi.OnInt() { @Override public void on(int v) { Prefs.putInt(LinuxPrefs.ZOOM, v); LinuxWm.get().reloadPrefs(); } }));
        SettingsUi.add(a, g, SettingsUi.switchRow(a, I18n.t("lx_sharp"), LinuxPrefs.sharp(), new SettingsUi.OnBool() {
            @Override public void on(boolean v) { Prefs.putBool(LinuxPrefs.SHARP, v); LinuxWm.get().reloadPrefs(); }
        }));
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("lx_csd"), new String[]{I18n.t("lx_csd_always"), I18n.t("lx_csd_hide")}, LinuxPrefs.csdMode(),
                new SettingsUi.OnInt() { @Override public void on(int v) { Prefs.putInt(LinuxPrefs.CSD, v); LinuxWm.get().reloadPrefs(); a.rebuild(); } }));
        out.addView(g);
    }

    // ---------------- Власні розкладки ----------------

    private static void layoutsBlock(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("lx_layouts_title")));
        LinearLayout g = SettingsUi.group(a);
        g.addView(pad(a, Ui.text(a, I18n.t("lx_layouts_hint"), 13, SettingsUi.TEXT_DIM), 16, 12, 16, 4));
        for (final Object[] l : LinuxWm.customLayouts()) {
            final String id = (String) l[0], nm = (String) l[1], zs = (String) l[2];
            SettingsUi.add(a, g, itemRow(a, nm, zs, new Runnable() { @Override public void run() { layoutDialog(a, id, nm, zs); } },
                    new Runnable() { @Override public void run() { LinuxWm.deleteLayout(id); a.rebuild(); } }));
        }
        SettingsUi.divider(a, g);
        g.addView(pad(a, button(a, "+ " + I18n.t("lx_layout_add"), Theme.ACCENT, new Runnable() { @Override public void run() { layoutDialog(a, null, "", ""); } }), 16, 10, 16, 12));
        out.addView(g);
    }

    private static void layoutDialog(final SettingsActivity a, final String id, String nm, String zs) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 20), 0);
        final EditText name = field(a, I18n.t("lx_layout_name"), false);
        final EditText zones = field(a, I18n.t("lx_layout_zones"), true);
        name.setText(nm); zones.setText(zs);
        box.addView(name); box.addView(zones);
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(I18n.t("lx_layout_add")).setView(box)
                .setPositiveButton(I18n.t("lx_save"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (!LinuxWm.saveLayout(id, name.getText().toString().trim(), zones.getText().toString())) {
                            Toast.makeText(a, I18n.t("lx_layout_bad"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        a.rebuild();
                    }
                }).setNegativeButton(I18n.t("cancel"), null).show();
    }
}
