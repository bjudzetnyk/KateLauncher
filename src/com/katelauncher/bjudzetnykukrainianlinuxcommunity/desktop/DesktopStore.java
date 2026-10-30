package com.katelauncher.bjudzetnykukrainianlinuxcommunity.desktop;

import android.text.TextUtils;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppEntry;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Config;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;

import java.util.ArrayList;
import java.util.List;

public final class DesktopStore {
    private DesktopStore() {}

    public static List<DesktopItem> load() {
        List<DesktopItem> out = new ArrayList<>();
        String s = Prefs.getString(Prefs.DESKTOP_ITEMS, "");
        if (s.isEmpty()) return out;
        for (String line : s.split("\n")) {
            String[] p = line.split(";");
            if (p.length != 3) continue;
            try { out.add(new DesktopItem(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]))); }
            catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public static void save(List<DesktopItem> items) {
        List<String> lines = new ArrayList<>();
        for (DesktopItem it : items) lines.add(it.key + ";" + it.col + ";" + it.row);
        Prefs.putString(Prefs.DESKTOP_ITEMS, TextUtils.join("\n", lines));
    }

    public static boolean addApp(AppEntry e) { return addKey(e.key); }

    /** Додає ключ (програми або папки "folder:id") у першу вільну клітинку. */
    public static boolean addKey(String key) {
        List<DesktopItem> items = load();
        boolean[][] used = new boolean[Config.GRID_COLS][Config.GRID_ROWS];
        for (DesktopItem it : items) {
            if (it.col >= 0 && it.col < Config.GRID_COLS && it.row >= 0 && it.row < Config.GRID_ROWS) used[it.col][it.row] = true;
        }
        for (int r = 0; r < Config.GRID_ROWS; r++) {
            for (int c = 0; c < Config.GRID_COLS; c++) {
                if (!used[c][r]) {
                    items.add(new DesktopItem(key, c, r));
                    save(items);
                    return true;
                }
            }
        }
        return false;
    }

    public static void removePackage(String pkg) {
        List<DesktopItem> items = load();
        List<DesktopItem> keep = new ArrayList<>();
        for (DesktopItem it : items) if (!it.key.startsWith(pkg + "/")) keep.add(it);
        if (keep.size() != items.size()) save(keep);
    }
}
