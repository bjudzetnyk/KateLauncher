package com.gnome.launcher.core;

/** Кольори (спрощений стиль Ubuntu). ACCENT і DESKTOP_BG користувач може змінити в налаштуваннях. */
public final class Theme {
    private Theme() {}

    public static final int DEFAULT_DESKTOP_BG = 0xFF2C001E;
    public static final int DEFAULT_ACCENT = 0xFFE95420;

    public static int DESKTOP_BG = DEFAULT_DESKTOP_BG;
    public static final int DOCK_BG = 0xE61D1D1D;
    public static final int DRAWER_BG = 0xF2201A1D;
    public static final int SURFACE = 0xFF2B2B2B;
    public static final int SURFACE_HI = 0xFF3A3A3A;
    public static int ACCENT = DEFAULT_ACCENT;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFB8B8B8;

    /** Викликати один раз при старті процесу (App.onCreate), після Prefs.init(). */
    public static void init() {
        ACCENT = 0xFF000000 | Prefs.getInt(Prefs.ACCENT_COLOR, DEFAULT_ACCENT);
        DESKTOP_BG = 0xFF000000 | Prefs.getInt(Prefs.FILL_COLOR, DEFAULT_DESKTOP_BG);
    }

    public static void setAccent(int rgb) {
        Prefs.putInt(Prefs.ACCENT_COLOR, 0xFF000000 | rgb);
        ACCENT = 0xFF000000 | rgb;
    }

    public static void resetAccent() {
        Prefs.putInt(Prefs.ACCENT_COLOR, DEFAULT_ACCENT);
        ACCENT = DEFAULT_ACCENT;
    }

    public static void setFill(int rgb) {
        Prefs.putInt(Prefs.FILL_COLOR, 0xFF000000 | rgb);
        DESKTOP_BG = 0xFF000000 | rgb;
    }

    public static void resetFill() {
        Prefs.putInt(Prefs.FILL_COLOR, DEFAULT_DESKTOP_BG);
        DESKTOP_BG = DEFAULT_DESKTOP_BG;
    }

    /** ACCENT із заданою альфою (напр. 0x44 для підсвічування цілі перетягування). */
    public static int accentAlpha(int alpha) {
        return (alpha << 24) | (ACCENT & 0x00FFFFFF);
    }
}
