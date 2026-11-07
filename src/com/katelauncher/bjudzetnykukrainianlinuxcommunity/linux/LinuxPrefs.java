package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

import android.content.Context;
import android.util.DisplayMetrics;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;

/** Налаштування Linux-вікон і їх значення за замовчуванням. */
public final class LinuxPrefs {
    private LinuxPrefs() {}

    public static final String ENVS = "linux_envs", APPS = "linux_apps", TOKEN = "linux_token",
            OVERLAY = "linux_overlay", FPS = "linux_fps", SAVER = "linux_saver", ZOOM = "linux_zoom",
            SHARP = "linux_sharp", CSD = "linux_csd", DISPLAY = "linux_display", IDLE_MIN = "linux_idle_min", DEBUG = "linux_debug", LAYOUTS = "linux_layouts", ACTIVE_LAYOUT = "linux_layout_active";

    /** Постійний токен: лише процес-композитор, запущений лаунчером, знає його (перевіряється при підключенні). */
    public static String token() {
        String t = Prefs.getString(TOKEN, "");
        if (t.isEmpty()) {
            java.security.SecureRandom r = new java.security.SecureRandom();
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 24; i++) b.append(Integer.toString(r.nextInt(36), 36));
            t = b.toString();
            Prefs.putString(TOKEN, t);
        }
        return t;
    }

    /** false (за замовчуванням): вікна лише поверх робочого столу. true: поверх усіх програм. */
    public static boolean overlayAll() { return Prefs.getBool(OVERLAY, false); }
    public static boolean saver() { return Prefs.getBool(SAVER, false); }
    public static boolean sharp() { return Prefs.getBool(SHARP, false); }
    /** Режим показу програм за замовчуванням: "wayland" або "x11" (Xwayland). Можна перевизначити для середовища чи програми. */
    public static String defaultDisplay() { return "x11".equals(Prefs.getString(DISPLAY, "wayland")) ? "x11" : "wayland"; }
    /** Через скільки хвилин простою (без програм і вікон) завершувати сеанс у Termux; 0 — ніколи. */
    public static int idleMinutes() { int m = Prefs.getInt(IDLE_MIN, 5); return m < 0 ? 0 : Math.min(m, 240); }
    /** Докладний журнал композитора (вхід, фокус, X11) — у журнал сеансу. */
    public static boolean debug() { return Prefs.getBool(DEBUG, false); }
    /** 0 — рамка лаунчера завжди, 1 — ховати, якщо програма малює власну. */
    public static int csdMode() { return Prefs.getInt(CSD, 0); }

    /** Обмеження FPS; режим економії обмежує до 30. */
    public static int fps() {
        int f = Prefs.getInt(FPS, 30);
        if (f < 5) f = 5; if (f > 144) f = 144;
        return saver() ? Math.min(f, 30) : f;
    }

    /** Масштаб відображення логічного пікселя Linux у фізичні (percent/100). За замовчуванням — за щільністю екрана. */
    public static float zoom(Context c) {
        int p = Prefs.getInt(ZOOM, 0);
        if (p <= 0) {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            float z = dm.density * 0.55f;
            return Math.max(1f, Math.min(2.5f, z));
        }
        return Math.max(0.5f, Math.min(4f, p / 100f));
    }
    public static int zoomPercent(Context c) { return Math.round(zoom(c) * 100f); }
    public static int bufferScale() { return sharp() ? 2 : 1; }
}
