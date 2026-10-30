package com.katelauncher.bjudzetnykukrainianlinuxcommunity.core;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Папки програм: спільне сховище для робочого столу, дока й меню програм. */
public final class FolderStore {
    private FolderStore() {}

    public static final String PREFIX = "folder:";

    public static final class Folder {
        public final String id;
        public String name;
        public final List<String> apps = new ArrayList<>();
        Folder(String id, String name) { this.id = id; this.name = name; }
    }

    public static boolean isFolder(String key) { return key != null && key.startsWith(PREFIX); }
    public static String idOf(String key) { return key.substring(PREFIX.length()); }

    public static List<Folder> all() {
        List<Folder> out = new ArrayList<>();
        String s = Prefs.getString(Prefs.FOLDERS, "");
        if (s.isEmpty()) return out;
        for (String line : s.split("\n")) {
            String[] p = line.split(";", 3);
            if (p.length < 2) continue;
            Folder f = new Folder(p[0], p[1]);
            if (p.length == 3 && !p[2].isEmpty()) {
                for (String k : p[2].split(",")) if (!k.isEmpty()) f.apps.add(k);
            }
            out.add(f);
        }
        return out;
    }

    public static Folder find(String id) {
        for (Folder f : all()) if (f.id.equals(id)) return f;
        return null;
    }

    private static void saveAll(List<Folder> list) {
        List<String> lines = new ArrayList<>();
        for (Folder f : list) lines.add(f.id + ";" + f.name.replace(";", "") + ";" + TextUtils.join(",", f.apps));
        Prefs.putString(Prefs.FOLDERS, TextUtils.join("\n", lines));
    }

    public static String create(String name, List<String> initialApps) {
        List<Folder> list = all();
        String id = System.currentTimeMillis() + "" + (int) (Math.random() * 1000);
        Folder f = new Folder(id, name);
        f.apps.addAll(initialApps);
        list.add(f);
        saveAll(list);
        return id;
    }

    public static void rename(String id, String name) {
        List<Folder> list = all();
        for (Folder f : list) if (f.id.equals(id)) f.name = name;
        saveAll(list);
    }

    public static void addApp(String id, String appKey) {
        List<Folder> list = all();
        for (Folder f : list) if (f.id.equals(id) && !f.apps.contains(appKey)) f.apps.add(appKey);
        saveAll(list);
    }

    public static void removeApp(String id, String appKey) {
        List<Folder> list = all();
        for (Folder f : list) if (f.id.equals(id)) f.apps.remove(appKey);
        saveAll(list);
    }

    /** Новий порядок показаних програм; ключі, яких немає у списку (напр. видалені програми), лишаються в кінці. */
    public static void reorder(String id, List<String> shown) {
        List<Folder> list = all();
        for (Folder f : list) {
            if (!f.id.equals(id)) continue;
            List<String> rest = new ArrayList<>();
            for (String k : f.apps) if (!shown.contains(k)) rest.add(k);
            f.apps.clear();
            f.apps.addAll(shown);
            f.apps.addAll(rest);
        }
        saveAll(list);
    }

    public static void delete(String id) {
        List<Folder> list = all();
        List<Folder> keep = new ArrayList<>();
        for (Folder f : list) if (!f.id.equals(id)) keep.add(f);
        saveAll(keep);
    }
}
