package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockOverlayService;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockSide;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Config;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.SystemUi;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.LanguageRepository;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.wallpaper.WallpaperCropActivity;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.wallpaper.WallpaperStore;

/** Розділи налаштувань (як панелі в «Параметрах» Ubuntu) і їхній вміст. */
final class Panels {
    private Panels() {}

    enum Panel {
        ABOUT("about", "panel_about", SymbolView.INFO),
        SYSTEM("system", "panel_system", SymbolView.GEAR),
        PERSONAL("personal", "panel_personal", SymbolView.APPEARANCE);

        final String id, titleKey;
        final int symbol;
        Panel(String id, String titleKey, int symbol) { this.id = id; this.titleKey = titleKey; this.symbol = symbol; }

        static Panel byId(String id) {
            for (Panel p : values()) if (p.id.equals(id)) return p;
            return null;
        }
    }

    private static final String[] SHAPES = {"circle", "round", "square"};

    static void build(SettingsActivity a, Panel p, LinearLayout out) {
        if (p == Panel.ABOUT) about(a, out);
        else if (p == Panel.SYSTEM) system(a, out);
        else personal(a, out);
    }

    private static void section(SettingsActivity a, LinearLayout out, String key, boolean first) {
        android.widget.TextView t = Ui.text(a, I18n.t(key), 19, Theme.ACCENT);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(a, 6), Ui.dp(a, first ? 4 : 26), 0, 0);
        out.addView(t);
    }

    // ---------- Про програму ----------

    /**
     * «Про програму»: заголовок із назвою версії (фіксований, видільним кольором) і текст
     * нижче — звичайний плейсхолдер, який редагується у файлі локалізації (ключ
     * "about_placeholder"). Текст підтримує просту розмітку HTML:
     *   - посилання:               &lt;a href="https://example.com"&gt;текст&lt;/a&gt;
     *   - текст видільним кольором: &lt;font color='#E95420'&gt;текст&lt;/font&gt;
     *   - жирний / курсив:          &lt;b&gt;...&lt;/b&gt;, &lt;i&gt;...&lt;/i&gt;
     *   - новий рядок:              &lt;br&gt;
     * Щоб завжди використовувати поточний видільний колір інтерфейсу (а не сталий HEX),
     * можна залишити плейсхолдер і просто спитати розробника — колір рядка з назвою версії
     * підставляється кодом автоматично і в самому тексті його можна не дублювати.
     */
    private static void about(SettingsActivity a, LinearLayout out) {
        TextView title = Ui.text(a, "KateLauncher0.7.3", 21, Theme.ACCENT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 12));
        out.addView(title);

        TextView body = new TextView(a);
        body.setTextColor(SettingsUi.TEXT);
        body.setTextSize(15);
        body.setLineSpacing(Ui.dp(a, 3), 1f);
        body.setLinksClickable(true);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setText(Html.fromHtml(I18n.t("about_placeholder").replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY));
        body.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        out.addView(body);
    }

    /** Усе, що стосується вигляду, на одній сторінці. */
    private static void personal(SettingsActivity a, LinearLayout out) {
        section(a, out, "panel_background", true);
        background(a, out);
        section(a, out, "panel_appearance", false);
        appearance(a, out);
        section(a, out, "panel_dock", false);
        dockBehavior(a, out);
        icons(a, out, Prefs.DOCK_ICON_SHAPE, Prefs.DOCK_ICON_SIZE, 32, 72, Config.DOCK_ICON_DP);
        section(a, out, "panel_desktop", false);
        icons(a, out, Prefs.DESKTOP_ICON_SHAPE, Prefs.DESKTOP_ICON_SIZE, 32, 80, Config.DESKTOP_ICON_DP);
        section(a, out, "panel_drawer", false);
        icons(a, out, Prefs.DRAWER_ICON_SHAPE, Prefs.DRAWER_ICON_SIZE, 40, 96, Config.DRAWER_ICON_DP);
    }

    // ---------- Фон ----------

    private static void background(final SettingsActivity a, LinearLayout out) {
        boolean split = WallpaperStore.split();

        LinearLayout sg = SettingsUi.group(a);
        SettingsUi.add(a, sg, SettingsUi.switchRow(a, I18n.t("wallpaper_split"), split, new SettingsUi.OnBool() {
            @Override public void on(boolean v) {
                if (v) { WallpaperStore.setSplit(a, true); a.rebuild(); return; }
                boolean hp = WallpaperStore.has(a, false), hl = WallpaperStore.has(a, true);
                if (hp && hl) { askCollapse(a); return; }
                if (!hp && hl) { WallpaperStore.collapseTo(a, true); a.rebuild(); return; }
                WallpaperStore.setSplit(a, false);
                a.rebuild();
            }
        }));
        out.addView(sg);

        if (split) {
            out.addView(SettingsUi.groupTitle(a, I18n.t("orientation_port")));
            wallpaperSlot(a, out, false);
            out.addView(SettingsUi.groupTitle(a, I18n.t("orientation_land")));
            wallpaperSlot(a, out, true);
        } else {
            wallpaperSlot(a, out, false);
        }

        out.addView(SettingsUi.groupTitle(a, I18n.t("group_colors")));
        LinearLayout cg = SettingsUi.group(a);
        SettingsUi.add(a, cg, SettingsUi.actionRow(a, I18n.t("fill_color"), hex(Theme.DESKTOP_BG), new Runnable() {
            @Override public void run() {
                ColorPickerDialog.show(a, "fill_color", Theme.DESKTOP_BG & 0xFFFFFF, Theme.DEFAULT_DESKTOP_BG & 0xFFFFFF,
                        new ColorPickerDialog.OnPicked() {
                            @Override public void on(int rgb) { Theme.setFill(rgb); a.rebuild(); }
                        });
            }
        }));
        out.addView(cg);
    }

    private static String hex(int argb) { return String.format("#%06X", argb & 0xFFFFFF); }

    private static void askCollapse(final SettingsActivity a) {
        String[] opts = {I18n.t("orientation_port"), I18n.t("orientation_land")};
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("wallpaper_split_keep"))
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        WallpaperStore.collapseTo(a, which == 1);
                        a.rebuild();
                    }
                })
                .setNegativeButton(I18n.t("cancel"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { a.rebuild(); }
                })
                .show();
    }

    private static void wallpaperSlot(final SettingsActivity a, LinearLayout out, final boolean land) {
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        float ratio = (float) dm.widthPixels / Math.max(1, dm.heightPixels);
        int maxW = Math.min(Ui.dp(a, 360), SettingsUi.contentWidth(a) - Ui.dp(a, 32));
        int h = Ui.dp(a, 190);
        int w = Math.round(h * ratio);
        if (w > maxW) { w = maxW; h = Math.round(w / ratio); }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(a, 6);
        lp.bottomMargin = Ui.dp(a, 14);
        out.addView(new WallpaperPreview(a, land), lp);

        LinearLayout g = SettingsUi.group(a);
        SettingsUi.add(a, g, SettingsUi.actionRow(a, I18n.t("wallpaper_choose"), null, new Runnable() {
            @Override public void run() { pick(a, land); }
        }));
        if (WallpaperStore.has(a, land)) {
            SettingsUi.add(a, g, SettingsUi.actionRow(a, I18n.t("wallpaper_reset"), null, new Runnable() {
                @Override public void run() { WallpaperStore.clear(a, land); a.rebuild(); }
            }));
        }
        out.addView(g);
    }

    private static void pick(final SettingsActivity a, final boolean land) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        a.startForResult(i, new SettingsActivity.ResultHandler() {
            @Override public void onResult(int res, Intent data) {
                if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
                try { WallpaperStore.importPending(a, data.getData()); }
                catch (Exception e) { Toast.makeText(a, I18n.t("wallpaper_failed"), Toast.LENGTH_LONG).show(); return; }
                Intent crop = new Intent(a, WallpaperCropActivity.class);
                crop.putExtra(WallpaperCropActivity.EXTRA_LAND, land);
                a.startForResult(crop, new SettingsActivity.ResultHandler() {
                    @Override public void onResult(int r2, Intent d2) { a.rebuild(); }
                });
            }
        });
    }

    // ---------- Вигляд ----------

    private static void appearance(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("group_effects")));
        LinearLayout g = SettingsUi.group(a);
        SettingsUi.add(a, g, SettingsUi.switchRow(a, I18n.t("animations"), Prefs.getBool(Prefs.ANIM_ENABLED, true),
                new SettingsUi.OnBool() { @Override public void on(boolean v) { Prefs.putBool(Prefs.ANIM_ENABLED, v); } }));
        SettingsUi.add(a, g, SettingsUi.sliderRow(a, I18n.t("drawer_opacity"), 80, 255,
                Prefs.getInt(Prefs.DRAWER_BG_ALPHA, 242),
                new SettingsUi.Fmt() { @Override public String f(int v) { return Math.round(v * 100f / 255f) + "%"; } },
                new SettingsUi.OnInt() { @Override public void on(int v) { Prefs.putInt(Prefs.DRAWER_BG_ALPHA, v); } }));
        out.addView(g);

        out.addView(SettingsUi.groupTitle(a, I18n.t("group_accent")));
        LinearLayout ag = SettingsUi.group(a);
        SettingsUi.add(a, ag, SettingsUi.actionRow(a, I18n.t("accent_color"), hex(Theme.ACCENT), new Runnable() {
            @Override public void run() {
                ColorPickerDialog.show(a, "accent_color", Theme.ACCENT & 0xFFFFFF, Theme.DEFAULT_ACCENT & 0xFFFFFF,
                        new ColorPickerDialog.OnPicked() {
                            @Override public void on(int rgb) { Theme.setAccent(rgb); a.rebuild(); }
                        });
            }
        }));
        out.addView(ag);
    }

    // ---------- Док / Робочий стіл / Меню програм ----------

    private static void icons(final SettingsActivity a, LinearLayout out, final String shapeKey, final String sizeKey, int min, int max, int def) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("group_icons")));
        LinearLayout g = SettingsUi.group(a);

        final String[] labels = {I18n.t("shape_circle"), I18n.t("shape_round"), I18n.t("shape_square")};
        String cur = Prefs.getString(shapeKey, "round");
        int sel = 1;
        for (int i = 0; i < SHAPES.length; i++) if (SHAPES[i].equals(cur)) sel = i;
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("icon_shape"), labels, sel, new SettingsUi.OnInt() {
            @Override public void on(int i) { Prefs.putString(shapeKey, SHAPES[i]); a.rebuild(); }
        }));
        SettingsUi.add(a, g, dpSlider(a, "icon_size", sizeKey, min, max, def));
        out.addView(g);
    }

    private static android.view.View dpSlider(SettingsActivity a, String titleKey, final String prefKey, int min, int max, int def) {
        return SettingsUi.sliderRow(a, I18n.t(titleKey), min, max, Prefs.getInt(prefKey, def),
                new SettingsUi.Fmt() { @Override public String f(int v) { return v + " dp"; } },
                new SettingsUi.OnInt() { @Override public void on(int v) { Prefs.putInt(prefKey, v); } });
    }

    // ---------- Поведінка й положення дока ----------

    private static void dockBehavior(final SettingsActivity a, LinearLayout out) {
        out.addView(SettingsUi.groupTitle(a, I18n.t("group_dock_behavior")));
        LinearLayout g = SettingsUi.group(a);

        String[] modes = {I18n.t("dock_mode_desktop"), I18n.t("dock_mode_overlay")};
        int mode = Prefs.getInt(Prefs.DOCK_MODE, 0) == 1 ? 1 : 0;
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("dock_mode"), modes, mode, new SettingsUi.OnInt() {
            @Override public void on(int i) {
                if (i == 1 && !Settings.canDrawOverlays(a)) {
                    Toast.makeText(a, I18n.t("overlay_permission"), Toast.LENGTH_LONG).show();
                    Intent in = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName()));
                    a.startForResult(in, new SettingsActivity.ResultHandler() {
                        @Override public void onResult(int res, Intent data) {
                            if (Settings.canDrawOverlays(a)) {
                                Prefs.putInt(Prefs.DOCK_MODE, 1);
                                DockOverlayService.sync(a);
                            }
                            a.rebuild();
                        }
                    });
                    return;
                }
                Prefs.putInt(Prefs.DOCK_MODE, i);
                DockOverlayService.sync(a);
                a.rebuild();
            }
        }));

        String[] yn = {I18n.t("yes"), I18n.t("no")};
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("dock_shift"), yn,
                Prefs.getBool(Prefs.DOCK_SHIFT, true) ? 0 : 1, new SettingsUi.OnInt() {
            @Override public void on(int i) { Prefs.putBool(Prefs.DOCK_SHIFT, i == 0); a.rebuild(); }
        }));

        out.addView(g);

        // Положення дока і навігації — окремо для книжкової та альбомної орієнтацій.
        out.addView(SettingsUi.groupTitle(a, I18n.t("dock_group_port")));
        dockPlacement(a, out, false);
        out.addView(SettingsUi.groupTitle(a, I18n.t("dock_group_land")));
        dockPlacement(a, out, true);
    }

    /**
     * Два вибори для однієї орієнтації:
     *  1) «Положення дока»: Зліва / Справа / Зверху / Знизу;
     *  2) «Положення навігації» (кнопки меню програм і налаштувань на доці):
     *     док Зліва/Справа -> Зверху/Знизу, док Зверху/Знизу -> Зліва/Справа.
     */
    private static void dockPlacement(final SettingsActivity a, LinearLayout out, final boolean land) {
        LinearLayout g = SettingsUi.group(a);
        final String[] pos = {I18n.t("pos_left"), I18n.t("pos_right"), I18n.t("pos_top"), I18n.t("pos_bottom")};
        int side = DockSide.side(land);
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("dock_position"), pos, side, new SettingsUi.OnInt() {
            @Override public void on(int i) { DockSide.setSide(land, i); a.rebuild(); }
        }));

        boolean vertical = !DockSide.horizontal(side);
        String[] opts = vertical ? new String[]{pos[2], pos[3]} : new String[]{pos[0], pos[1]};
        int sel = DockSide.navAtEnd(land) ? 1 : 0;
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("dock_navigation"), opts, sel, new SettingsUi.OnInt() {
            @Override public void on(int i) { DockSide.setNavAtEnd(land, i == 1); a.rebuild(); }
        }));
        out.addView(g);
    }

    private static void restart(Activity a) {
        Intent home = new Intent(a, com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(a, 77, home, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC, System.currentTimeMillis() + 300, pi);
        a.finishAffinity();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { Process.killProcess(Process.myPid()); }
        }, 150);
    }

    // ---------- Система ----------

    private static void system(final SettingsActivity a, LinearLayout out) {
        // Екран
        out.addView(SettingsUi.groupTitle(a, I18n.t("group_display")));
        LinearLayout g = SettingsUi.group(a);
        SettingsUi.add(a, g, SettingsUi.switchRow(a, I18n.t("hide_nav"), Prefs.getBool(Prefs.NAV_HIDDEN, false),
                new SettingsUi.OnBool() {
                    @Override public void on(boolean v) { Prefs.putBool(Prefs.NAV_HIDDEN, v); SystemUi.apply(a); }
                }));
        String[] names = {I18n.t("orient_system"), I18n.t("orient_landscape"), I18n.t("orient_portrait")};
        int cur = Math.max(0, Math.min(2, Prefs.getInt(Prefs.ORIENTATION, 0)));
        SettingsUi.add(a, g, SettingsUi.comboRow(a, I18n.t("orientation"), names, cur, new SettingsUi.OnInt() {
            @Override public void on(int i) {
                Prefs.putInt(Prefs.ORIENTATION, i);
                a.rebuild();
                SystemUi.apply(a);
            }
        }));
        out.addView(g);

        // Лаунчер
        out.addView(SettingsUi.groupTitle(a, I18n.t("group_launcher")));
        LinearLayout g2 = SettingsUi.group(a);
        SettingsUi.add(a, g2, SettingsUi.actionRow(a, I18n.t("language"), LanguageRepository.nameOf(a, I18n.code()), new Runnable() {
            @Override public void run() {
                LanguageDialog.show(a, new Runnable() { @Override public void run() { a.rebuild(); } });
            }
        }));
        SettingsUi.add(a, g2, DefaultLauncher.row(a));
        SettingsUi.add(a, g2, SettingsUi.dangerRow(a, I18n.t("restart"), new Runnable() {
            @Override public void run() { restart(a); }
        }));
        out.addView(g2);

        // Резервна копія персоналізації
        out.addView(SettingsUi.groupTitle(a, I18n.t("group_backup")));
        LinearLayout g3 = SettingsUi.group(a);
        SettingsUi.add(a, g3, SettingsUi.actionRow(a, I18n.t("backup_export"), null, new Runnable() {
            @Override public void run() { BackupDialog.showExport(a); }
        }));
        SettingsUi.add(a, g3, SettingsUi.actionRow(a, I18n.t("backup_import"), null, new Runnable() {
            @Override public void run() { BackupDialog.showImport(a); }
        }));
        out.addView(g3);
    }
}
