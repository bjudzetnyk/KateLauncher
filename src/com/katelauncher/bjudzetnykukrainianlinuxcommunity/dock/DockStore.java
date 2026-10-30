package com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock;

import android.text.TextUtils;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;

import java.util.ArrayList;
import java.util.List;

/** Список обраних застосунків дока (ключі "пакет/клас"). */
public final class DockStore {
    private DockStore() {}

    public static List<String> list() {
        List<String> l = new ArrayList<>();
        String s = Prefs.getString(Prefs.DOCK_ITEMS, "");
        if (!s.isEmpty()) for (String k : s.split("\n")) if (!k.isEmpty()) l.add(k);
        return l;
    }

    public static void saveOrder(List<String> l) { save(l); }

    private static void save(List<String> l) {
        Prefs.putString(Prefs.DOCK_ITEMS, TextUtils.join("\n", l));
    }

    public static boolean add(String key) {
        List<String> l = list();
        if (l.contains(key)) return false;
        l.add(key);
        save(l);
        return true;
    }

    public static void remove(String key) {
        List<String> l = list();
        if (l.remove(key)) save(l);
    }

    public static void removePackage(String pkg) {
        List<String> l = list();
        List<String> keep = new ArrayList<>();
        for (String k : l) if (!k.startsWith(pkg + "/")) keep.add(k);
        if (keep.size() != l.size()) save(keep);
    }
}
