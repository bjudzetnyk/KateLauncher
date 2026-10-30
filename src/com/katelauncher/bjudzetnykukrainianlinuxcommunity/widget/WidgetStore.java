package com.gnome.launcher.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;

import com.gnome.launcher.core.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Збереження віджетів у клітинках сітки робочого столу.
 * За замовчуванням позиція/розмір СПІЛЬНІ для книжкової й ландшафтної орієнтації
 * (просто вписуються у поточну сітку) — тому поворот екрана більше не "ламає" місце.
 * Якщо користувач явно вибрав у меню "Зберегти позицію у: …", для тієї орієнтації
 * з'являється окремий, незалежний запис (override), який більше не чіпає спільний.
 */
public final class WidgetStore {
    private WidgetStore() {}

    public static final class Entry {
        public final boolean gif;
        public int id;       // appWidgetId для APP
        public String path;  // ім'я файлу гіфки для GIF
        public int col, row, w, h;          // спільна (базова) позиція/розмір
        public int[] port;   // override для книжкової {col,row,w,h} або null
        public int[] land;   // override для ландшафтної {col,row,w,h} або null

        public Entry(int id, int col, int row, int w, int h) { this.gif = false; this.id = id; this.col = col; this.row = row; this.w = w; this.h = h; }
        public Entry(String path, int col, int row, int w, int h) { this.gif = true; this.path = path; this.col = col; this.row = row; this.w = w; this.h = h; }

        public boolean hasOverride(boolean land) { return land ? this.land != null : this.port != null; }

        /** Поточне відображуване положення для заданої орієнтації (override або база). */
        public int[] effective(boolean landscape) {
            int[] o = landscape ? land : port;
            return o != null ? o : new int[]{col, row, w, h};
        }

        /** Записує нове положення туди, куди слід: у override поточної орієнтації, якщо він уже є, інакше в базу. */
        public void apply(boolean landscape, int col, int row, int w, int h) {
            int[] o = landscape ? land : port;
            if (o != null) { o[0] = col; o[1] = row; o[2] = w; o[3] = h; }
            else { this.col = col; this.row = row; this.w = w; this.h = h; }
        }

        /** Явне "Зберегти позицію у: …" — фіксує окремий override саме для цієї орієнтації. */
        public void saveOverride(boolean landscape, int col, int row, int w, int h) {
            int[] o = {col, row, w, h};
            if (landscape) land = o; else port = o;
        }
    }

    public static boolean isLandscape(Context c) {
        return c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static List<Entry> load(Context c) {
        List<Entry> out = new ArrayList<>();
        String s = Prefs.getString(Prefs.WIDGETS, "");
        if (s.isEmpty()) return out;
        for (String line : s.split("\n")) {
            if (line.isEmpty()) continue;
            String[] p = line.split(";");
            try {
                Entry e;
                int i;
                if ("GIF".equals(p[0])) {
                    e = new Entry(p[5], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                    i = 6;
                } else if ("APP".equals(p[0])) {
                    e = new Entry(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
                    i = 6;
                } else continue; // невідомий/застарілий формат
                for (; i < p.length; i++) {
                    if (p[i].startsWith("P:")) e.port = parse4(p[i].substring(2));
                    else if (p[i].startsWith("L:")) e.land = parse4(p[i].substring(2));
                }
                out.add(e);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static int[] parse4(String s) {
        String[] t = s.split(",");
        return new int[]{Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])};
    }

    private static String fmt4(int[] a) { return a[0] + "," + a[1] + "," + a[2] + "," + a[3]; }

    public static void save(Context c, List<Entry> list) {
        List<String> l = new ArrayList<>();
        for (Entry e : list) {
            StringBuilder sb = new StringBuilder();
            if (e.gif) sb.append("GIF;").append(e.col).append(';').append(e.row).append(';').append(e.w).append(';').append(e.h).append(';').append(e.path);
            else sb.append("APP;").append(e.id).append(';').append(e.col).append(';').append(e.row).append(';').append(e.w).append(';').append(e.h);
            if (e.port != null) sb.append(";P:").append(fmt4(e.port));
            if (e.land != null) sb.append(";L:").append(fmt4(e.land));
            l.add(sb.toString());
        }
        Prefs.putString(Prefs.WIDGETS, TextUtils.join("\n", l));
    }

    /** Чи перетинається клітинка (col,row) з якимось записом (за ефективним положенням для landscape), окрім except. */
    public static boolean occupied(List<Entry> list, boolean landscape, int col, int row, Entry except) {
        for (Entry e : list) {
            if (e == except) continue;
            int[] v = e.effective(landscape);
            if (col >= v[0] && col < v[0] + v[2] && row >= v[1] && row < v[1] + v[3]) return true;
        }
        return false;
    }
}
