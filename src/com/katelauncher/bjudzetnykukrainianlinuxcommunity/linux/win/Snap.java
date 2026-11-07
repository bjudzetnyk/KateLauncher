package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxPrefs;

/** Зони снапу (як у Windows 10), клавіші Super+стрілки та розкладки (каскад, тайлінг, власні). */
final class Snap {
    private Snap() {}

    static final int NONE = 0, MAX = 1, L = 2, R = 3, TL = 4, TR = 5, BL = 6, BR = 7;

    static Rect rect(int zone, Rect u) {
        int cx = (u.left + u.right) / 2, cy = (u.top + u.bottom) / 2;
        switch (zone) {
        case MAX: return new Rect(u);
        case L: return new Rect(u.left, u.top, cx, u.bottom);
        case R: return new Rect(cx, u.top, u.right, u.bottom);
        case TL: return new Rect(u.left, u.top, cx, cy);
        case TR: return new Rect(cx, u.top, u.right, cy);
        case BL: return new Rect(u.left, cy, cx, u.bottom);
        case BR: return new Rect(cx, cy, u.right, u.bottom);
        }
        return null;
    }

    /** Зона під пальцем/курсором біля країв: країв — halves, кути — чверті, верх — на весь екран. */
    static int detect(int px, int py, Rect u, int edge, int corner) {
        boolean left = px <= u.left + edge, right = px >= u.right - edge, top = py <= u.top + edge;
        boolean nearTop = py <= u.top + corner, nearBottom = py >= u.bottom - corner;
        if (left) return nearTop ? TL : nearBottom ? BL : L;
        if (right) return nearTop ? TR : nearBottom ? BR : R;
        if (top) return MAX;
        return NONE;
    }

    /** Super+стрілка: нова зона з поточної (-1 — згорнути). dir: 0 ліво, 1 право, 2 вгору, 3 вниз. */
    static int arrow(int zone, int dir) {
        switch (dir) {
        case 0: switch (zone) { case R: return NONE; case TR: return TL; case BR: return BL; case TL: case BL: case L: return zone; default: return L; }
        case 1: switch (zone) { case L: return NONE; case TL: return TR; case BL: return BR; case TR: case BR: case R: return zone; default: return R; }
        case 2: switch (zone) { case L: return TL; case R: return TR; case BL: return L; case BR: return R; default: return MAX; }
        default: switch (zone) { case MAX: return NONE; case L: return BL; case R: return BR; case TL: return L; case TR: return R; case BL: case BR: return NONE; default: return -1; }
        }
    }

    // ---------- Розкладки ----------
    static final class Layout {
        final String id, name; final List<float[]> zones;   // x,y,w,h у частках [0..1]
        Layout(String id, String name, List<float[]> z) { this.id = id; this.name = name; zones = z; }
        List<Rect> rects(Rect u) {
            List<Rect> l = new ArrayList<>();
            for (float[] z : zones) l.add(new Rect(u.left + Math.round(z[0] * u.width()), u.top + Math.round(z[1] * u.height()),
                    u.left + Math.round((z[0] + z[2]) * u.width()), u.top + Math.round((z[1] + z[3]) * u.height())));
            return l;
        }
    }

    static List<Layout> custom() {
        List<Layout> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.getString(LinuxPrefs.LAYOUTS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i); JSONArray za = o.getJSONArray("z"); List<float[]> zs = new ArrayList<>();
                for (int j = 0; j < za.length(); j++) { JSONArray q = za.getJSONArray(j); zs.add(new float[]{(float) q.getDouble(0), (float) q.getDouble(1), (float) q.getDouble(2), (float) q.getDouble(3)}); }
                if (!zs.isEmpty()) l.add(new Layout(o.getString("id"), o.getString("name"), zs));
            }
        } catch (Exception ignored) {}
        return l;
    }

    static void saveCustom(List<Layout> l) {
        try {
            JSONArray a = new JSONArray();
            for (Layout x : l) {
                JSONArray za = new JSONArray();
                for (float[] z : x.zones) za.put(new JSONArray().put(z[0]).put(z[1]).put(z[2]).put(z[3]));
                a.put(new JSONObject().put("id", x.id).put("name", x.name).put("z", za));
            }
            Prefs.putString(LinuxPrefs.LAYOUTS, a.toString());
        } catch (Exception ignored) {}
    }

    /** Розбір «x y w h; x y w h» у відсотках (0–100). null — помилка. */
    static List<float[]> parseZones(String s) {
        List<float[]> out = new ArrayList<>();
        try {
            for (String part : s.split("[;\\n]")) {
                part = part.trim(); if (part.isEmpty()) continue;
                String[] t = part.split("[\\s,]+");
                if (t.length != 4) return null;
                float x = Float.parseFloat(t[0]) / 100f, y = Float.parseFloat(t[1]) / 100f, w = Float.parseFloat(t[2]) / 100f, h = Float.parseFloat(t[3]) / 100f;
                if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > 1.001f || y + h > 1.001f) return null;
                out.add(new float[]{x, y, w, h});
            }
        } catch (Exception e) { return null; }
        return out.isEmpty() ? null : out;
    }

    static Layout find(String id) { for (Layout l : custom()) if (l.id.equals(id)) return l; return null; }

    /** Тайлінг n вікон у сітці в області u. */
    static List<Rect> tile(int n, Rect u) {
        List<Rect> l = new ArrayList<>();
        if (n <= 0) return l;
        int cols = (int) Math.ceil(Math.sqrt(n)), rows = (int) Math.ceil(n / (double) cols);
        int cw = u.width() / cols, ch = u.height() / rows;
        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            int inRow = r == rows - 1 ? n - r * cols : cols;
            int w = u.width() / inRow;
            l.add(new Rect(u.left + c * w, u.top + r * ch, c == inRow - 1 ? u.right : u.left + (c + 1) * w, r == rows - 1 ? u.bottom : u.top + (r + 1) * ch));
        }
        return l;
    }
}
