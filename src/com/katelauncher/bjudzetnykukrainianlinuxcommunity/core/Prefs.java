package com.gnome.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}

    public static final String NAV_HIDDEN = "nav_hidden";
    public static final String ORIENTATION = "orientation";
    public static final String LANG = "lang";
    public static final String DOCK_HIDDEN = "dock_hidden";
    public static final String DOCK_ITEMS = "dock_items";
    public static final String DESKTOP_ITEMS = "desktop_items";
    public static final String WP_ON = "wp_on";
    public static final String WP_VER = "wp_ver";
    public static final String WP_L = "wp_l";
    public static final String WP_T = "wp_t";
    public static final String WP_R = "wp_r";
    public static final String WP_B = "wp_b";

    public static final String DOCK_ICON_SIZE = "dock_icon_size";
    public static final String DESKTOP_ICON_SIZE = "desktop_icon_size";
    public static final String DRAWER_ICON_SIZE = "drawer_icon_size";
    public static final String DOCK_ICON_SHAPE = "dock_icon_shape";
    public static final String DESKTOP_ICON_SHAPE = "desktop_icon_shape";
    public static final String DRAWER_ICON_SHAPE = "drawer_icon_shape";
    public static final String DRAWER_BG_ALPHA = "drawer_bg_alpha";
    public static final String ANIM_ENABLED = "anim_enabled";
    public static final String WIDGETS = "widgets";
    public static final String WIDGETS_LAND = "widgets_land";
    public static final String FOLDERS = "folders";
    public static final String DOCK_MODE = "dock_mode";       // 0 — лише стіл і меню, 1 — поверх усіх програм
    public static final String DOCK_SHIFT = "dock_shift";     // зсувати іконки столу за доком
    public static final String DOCK_POS_PORT = "dock_pos_port";
    public static final String DOCK_POS_LAND = "dock_pos_land";
    // Положення кнопок навігації (меню програм і налаштування) на доці, окремо для кожної орієнтації:
    // 0 — на початку дока (зверху / зліва), 1 — наприкінці (знизу / справа). Див. DockSide.
    public static final String DOCK_NAV_PORT = "dock_nav_port";
    public static final String DOCK_NAV_LAND = "dock_nav_land";

    public static final String ACCENT_COLOR = "accent_color";
    public static final String FILL_COLOR = "fill_color";

    // Шпалери: окремо для книжкової/альбомної орієнтації.
    public static final String WP_SPLIT = "wp_split";
    public static final String WP_ON_L = "wp_on_land";
    public static final String WP_L_L = "wp_l_land";
    public static final String WP_T_L = "wp_t_land";
    public static final String WP_R_L = "wp_r_land";
    public static final String WP_B_L = "wp_b_land";

    private static SharedPreferences sp;

    public static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("gnome_launcher", Context.MODE_PRIVATE);
    }

    public static boolean getBool(String k, boolean d) { return sp.getBoolean(k, d); }
    public static void putBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public static int getInt(String k, int d) { return sp.getInt(k, d); }
    public static void putInt(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public static float getFloat(String k, float d) { return sp.getFloat(k, d); }
    public static void putFloat(String k, float v) { sp.edit().putFloat(k, v).apply(); }
    public static String getString(String k, String d) { return sp.getString(k, d); }
    public static void putString(String k, String v) { sp.edit().putString(k, v).apply(); }
}
