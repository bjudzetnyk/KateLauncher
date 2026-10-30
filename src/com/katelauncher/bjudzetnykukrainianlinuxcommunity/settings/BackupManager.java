package com.gnome.launcher.settings;

import android.content.Context;
import android.net.Uri;

import com.gnome.launcher.core.Prefs;
import com.gnome.launcher.core.Theme;
import com.gnome.launcher.wallpaper.WallpaperStore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Експорт/імпорт налаштувань персоналізації одним файлом-архівом (звичайний zip:
 * settings.json +, за потреби, файли шпалер). Віджети й мова ніколи до нього не входять.
 * Імпорт змінює лише ті ключі, що справді присутні у файлі експорту.
 */
final class BackupManager {
    private BackupManager() {}

    private static final char T_BOOL = 'b', T_INT = 'i', T_FLOAT = 'f', T_STRING = 's';

    static final class Category {
        final String id, titleKey;
        final String[] keys;
        Category(String id, String titleKey, String... keys) { this.id = id; this.titleKey = titleKey; this.keys = keys; }
    }

    /** Усі категорії, що можна експортувати. Віджети (Prefs.WIDGETS*) і мова (Prefs.LANG)
     *  навмисно відсутні — вони не є частиною резервної копії персоналізації. */
    static final Category[] CATEGORIES = {
            new Category("wallpaper", "backup_cat_wallpaper",
                    Prefs.WP_ON, Prefs.WP_L, Prefs.WP_T, Prefs.WP_R, Prefs.WP_B,
                    Prefs.WP_SPLIT, Prefs.WP_ON_L, Prefs.WP_L_L, Prefs.WP_T_L, Prefs.WP_R_L, Prefs.WP_B_L),
            new Category("colors", "backup_cat_colors", Prefs.ACCENT_COLOR, Prefs.FILL_COLOR),
            new Category("dock", "backup_cat_dock",
                    Prefs.DOCK_MODE, Prefs.DOCK_SHIFT, Prefs.DOCK_ICON_SIZE, Prefs.DOCK_ICON_SHAPE),
            new Category("dock_position", "backup_cat_dock_position", Prefs.DOCK_POS_PORT, Prefs.DOCK_POS_LAND),
            new Category("dock_nav", "backup_cat_dock_nav", Prefs.DOCK_NAV_PORT, Prefs.DOCK_NAV_LAND),
            new Category("desktop", "backup_cat_desktop",
                    Prefs.DESKTOP_ICON_SIZE, Prefs.DESKTOP_ICON_SHAPE),
            new Category("drawer", "backup_cat_drawer",
                    Prefs.DRAWER_ICON_SIZE, Prefs.DRAWER_ICON_SHAPE, Prefs.DRAWER_BG_ALPHA),
            new Category("effects", "backup_cat_effects", Prefs.ANIM_ENABLED),
            new Category("system", "backup_cat_system", Prefs.NAV_HIDDEN, Prefs.ORIENTATION),
            new Category("layout", "backup_cat_layout", Prefs.DOCK_ITEMS, Prefs.DESKTOP_ITEMS, Prefs.FOLDERS),
    };

    /** Явна таблиця типів — SharedPreferences кидає ClassCastException, якщо читати ключ
     *  неправильним getter'ом, тож типи не вгадуємо, а знаємо наперед. */
    private static final Map<String, Character> TYPES = new HashMap<>();
    static {
        put(T_BOOL, Prefs.WP_ON, Prefs.WP_SPLIT, Prefs.WP_ON_L, Prefs.DOCK_SHIFT, Prefs.ANIM_ENABLED,
                Prefs.NAV_HIDDEN, Prefs.DOCK_HIDDEN);
        put(T_INT, Prefs.DOCK_MODE, Prefs.DOCK_POS_PORT, Prefs.DOCK_POS_LAND, Prefs.DOCK_NAV_PORT,
                Prefs.DOCK_NAV_LAND, Prefs.DOCK_ICON_SIZE, Prefs.DESKTOP_ICON_SIZE, Prefs.DRAWER_ICON_SIZE,
                Prefs.DRAWER_BG_ALPHA, Prefs.ORIENTATION,
                Prefs.ACCENT_COLOR, Prefs.FILL_COLOR, Prefs.WP_VER);
        put(T_FLOAT, Prefs.WP_L, Prefs.WP_T, Prefs.WP_R, Prefs.WP_B, Prefs.WP_L_L, Prefs.WP_T_L, Prefs.WP_R_L, Prefs.WP_B_L);
        put(T_STRING, Prefs.DOCK_ICON_SHAPE, Prefs.DESKTOP_ICON_SHAPE, Prefs.DRAWER_ICON_SHAPE,
                Prefs.DOCK_ITEMS, Prefs.DESKTOP_ITEMS, Prefs.FOLDERS, Prefs.LANG);
    }

    private static void put(char type, String... keys) {
        for (String k : keys) TYPES.put(k, type);
    }

    // ---- Експорт ----

    static void export(Context c, Uri dest, Set<String> categoryIds) throws Exception {
        JSONObject prefs = new JSONObject();
        boolean wallpaper = categoryIds.contains("wallpaper");
        for (Category cat : CATEGORIES) {
            if (!categoryIds.contains(cat.id)) continue;
            for (String k : cat.keys) prefs.put(k, read(k));
        }

        try (OutputStream raw = c.getContentResolver().openOutputStream(dest, "wt")) {
            if (raw == null) throw new IOException("no stream");
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                zip.putNextEntry(new ZipEntry("settings.json"));
                zip.write(prefs.toString(2).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                if (wallpaper) {
                    addFile(zip, "wp_current", WallpaperStore.current(c, false));
                    addFile(zip, "wp_current_land", WallpaperStore.current(c, true));
                }
            }
        }
    }

    private static String read(String key) {
        Character t = TYPES.get(key);
        if (t == null) t = T_STRING;
        switch (t) {
            case T_BOOL: return T_BOOL + ":" + Prefs.getBool(key, false);
            case T_INT: return T_INT + ":" + Prefs.getInt(key, 0);
            case T_FLOAT: return T_FLOAT + ":" + Prefs.getFloat(key, 0f);
            default: return T_STRING + ":" + Prefs.getString(key, "");
        }
    }

    private static void addFile(ZipOutputStream zip, String name, File f) throws IOException {
        if (!f.exists()) return;
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
        }
        zip.closeEntry();
    }

    // ---- Імпорт ----

    /** Повертає кількість застосованих ключів. Змінює лише те, що є у файлі. */
    static int importFrom(Context c, Uri src) throws Exception {
        JSONObject prefs = null;
        byte[] wpCurrent = null, wpLand = null;
        try (InputStream raw = c.getContentResolver().openInputStream(src)) {
            if (raw == null) throw new IOException("no stream");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if ("settings.json".equals(e.getName())) prefs = new JSONObject(readAll(zip));
                    else if ("wp_current".equals(e.getName())) wpCurrent = readAllBytes(zip);
                    else if ("wp_current_land".equals(e.getName())) wpLand = readAllBytes(zip);
                }
            }
        }
        if (prefs == null) throw new IOException("не файл резервної копії");

        int applied = 0;
        Iterator<String> it = prefs.keys();
        while (it.hasNext()) {
            String key = it.next();
            if (Prefs.WIDGETS.equals(key) || Prefs.WIDGETS_LAND.equals(key) || Prefs.LANG.equals(key)) continue; // ніколи
            String raw = prefs.optString(key, null);
            if (raw == null || raw.length() < 2) continue;
            char type = raw.charAt(0);
            String v = raw.substring(2);
            try {
                switch (type) {
                    case T_BOOL: Prefs.putBool(key, Boolean.parseBoolean(v)); break;
                    case T_INT: Prefs.putInt(key, Integer.parseInt(v)); break;
                    case T_FLOAT: Prefs.putFloat(key, Float.parseFloat(v)); break;
                    default: Prefs.putString(key, v);
                }
                applied++;
            } catch (NumberFormatException ignored) {}
        }
        if (wpCurrent != null) writeFile(WallpaperStore.current(c, false), wpCurrent);
        if (wpLand != null) writeFile(WallpaperStore.current(c, true), wpLand);
        if (wpCurrent != null || wpLand != null) Prefs.putInt(Prefs.WP_VER, Prefs.getInt(Prefs.WP_VER, 0) + 1);
        Theme.init();
        return applied;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) { out.write(data); }
    }
}
