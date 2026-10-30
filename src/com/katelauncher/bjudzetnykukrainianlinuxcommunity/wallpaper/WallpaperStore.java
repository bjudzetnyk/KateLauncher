package com.gnome.launcher.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;

import com.gnome.launcher.core.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Файли шпалер: wp_pending (щойно вибраний, спільний буфер), wp_current / wp_current_land
 * (застосовані — книжкова/«спільна» і, якщо увімкнено окремі шпалери, альбомна).
 * Обрізка зберігається як частки (l,t,r,b) від зображення — працює і для GIF.
 *
 * Коли Prefs.WP_SPLIT вимкнено, використовується лише «книжковий» слот (wp_current) для
 * обох орієнтацій. Коли увімкнено — у кожної орієнтації свій файл і своя обрізка.
 */
public final class WallpaperStore {
    private WallpaperStore() {}

    public static boolean split() { return Prefs.getBool(Prefs.WP_SPLIT, false); }

    public static File pending(Context c) { return new File(c.getFilesDir(), "wp_pending"); }

    public static File current(Context c, boolean land) {
        return new File(c.getFilesDir(), land ? "wp_current_land" : "wp_current");
    }

    public static boolean has(Context c, boolean land) {
        boolean on = land ? Prefs.getBool(Prefs.WP_ON_L, false) : Prefs.getBool(Prefs.WP_ON, false);
        return on && current(c, land).exists();
    }

    /** Чи дійсно варто показувати шпалери для цієї орієнтації просто зараз (з урахуванням split). */
    public static boolean active(Context c, boolean land) {
        return has(c, split() && land);
    }

    public static RectF crop(boolean land) {
        if (land) {
            return new RectF(
                    Prefs.getFloat(Prefs.WP_L_L, 0f), Prefs.getFloat(Prefs.WP_T_L, 0f),
                    Prefs.getFloat(Prefs.WP_R_L, 1f), Prefs.getFloat(Prefs.WP_B_L, 1f));
        }
        return new RectF(
                Prefs.getFloat(Prefs.WP_L, 0f), Prefs.getFloat(Prefs.WP_T, 0f),
                Prefs.getFloat(Prefs.WP_R, 1f), Prefs.getFloat(Prefs.WP_B, 1f));
    }

    public static void importPending(Context c, Uri uri) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(pending(c))) {
            if (in == null) throw new IOException("no stream");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** Застосувати вибраний файл як є (фото без повороту або GIF) з обрізкою f. */
    public static void apply(Context c, RectF f, boolean land) throws IOException {
        File cur = current(c, land);
        if (cur.exists()) cur.delete();
        if (!pending(c).renameTo(cur)) throw new IOException("rename failed");
        save(f, land);
    }

    /** Застосувати повернуте зображення (зберігається перекодованим у JPEG). */
    public static void applyBaked(Context c, Bitmap b, RectF f, boolean land) throws IOException {
        try (OutputStream out = new FileOutputStream(current(c, land))) {
            b.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        pending(c).delete();
        save(f, land);
    }

    public static void clear(Context c, boolean land) {
        current(c, land).delete();
        if (!land) pending(c).delete();
        if (land) Prefs.putBool(Prefs.WP_ON_L, false); else Prefs.putBool(Prefs.WP_ON, false);
        bump();
    }

    public static void clearAll(Context c) {
        clear(c, false);
        clear(c, true);
    }

    /** Вмикає/вимикає окремі шпалери для орієнтацій. При вмиканні копіює поточну (книжкову)
     *  шпалеру в альбомний слот, щоб вигляд відразу після перемикача не змінився. */
    public static void setSplit(Context c, boolean on) {
        if (on && !has(c, true) && has(c, false)) copy(c, false, true);
        Prefs.putBool(Prefs.WP_SPLIT, on);
    }

    /** Вимикає окремі шпалери, залишаючи для обох орієнтацій ту, що з `keepLand`. */
    public static void collapseTo(Context c, boolean keepLand) {
        if (keepLand) copy(c, true, false);
        clear(c, true);
        Prefs.putBool(Prefs.WP_SPLIT, false);
    }

    private static void copy(Context c, boolean fromLand, boolean toLand) {
        File src = current(c, fromLand);
        if (!src.exists()) return;
        File dst = current(c, toLand);
        try (InputStream in = new java.io.FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException ignored) {
            return;
        }
        RectF f = crop(fromLand);
        save(f, toLand);
    }

    private static void save(RectF f, boolean land) {
        if (land) {
            Prefs.putFloat(Prefs.WP_L_L, f.left);
            Prefs.putFloat(Prefs.WP_T_L, f.top);
            Prefs.putFloat(Prefs.WP_R_L, f.right);
            Prefs.putFloat(Prefs.WP_B_L, f.bottom);
            Prefs.putBool(Prefs.WP_ON_L, true);
        } else {
            Prefs.putFloat(Prefs.WP_L, f.left);
            Prefs.putFloat(Prefs.WP_T, f.top);
            Prefs.putFloat(Prefs.WP_R, f.right);
            Prefs.putFloat(Prefs.WP_B, f.bottom);
            Prefs.putBool(Prefs.WP_ON, true);
        }
        bump();
    }

    private static void bump() {
        Prefs.putInt(Prefs.WP_VER, Prefs.getInt(Prefs.WP_VER, 0) + 1);
    }
}
