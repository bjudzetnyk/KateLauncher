package com.katelauncher.bjudzetnykukrainianlinuxcommunity.core;

import android.text.TextUtils;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.desktop.DesktopItem;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.desktop.DesktopStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Папки програм. Три незалежні «простори»:
 *  - папки меню програм (id — самі цифри);
 *  - папки робочого столу й дока (id починається з «p») — кожна належить рівно одному місцю
 *    (або столу, або доку) і не залежить від папок меню програм чи від інших папок.
 * Додавання папки з меню програм на стіл/у док створює її незалежну КОПІЮ. Перетягування
 * папки між столом і доком переносить саму папку.
 */
public final class FolderStore {
    private FolderStore() {}

    public static final String PREFIX = "folder:";
    private static final String PLACED_MARK = "p";

    public static final int LOC_DRAWER = 0, LOC_DESKTOP = 1, LOC_DOCK = 2;

    public static final class Folder {
        public final String id;
        public String name;
        public final List<String> apps = new ArrayList<>();
        Folder(String id, String name) { this.id = id; this.name = name; }
        public boolean placed() { return id.startsWith(PLACED_MARK); }
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

    /** Лише папки меню програм. */
    public static List<Folder> drawerFolders() {
        List<Folder> out = new ArrayList<>();
        for (Folder f : all()) if (!f.placed()) out.add(f);
        return out;
    }

    /** Папки, що зараз лежать на столі (LOC_DESKTOP) або в доці (LOC_DOCK), або меню програм. */
    public static List<Folder> foldersAt(int loc) {
        if (loc == LOC_DRAWER) return drawerFolders();
        List<String> keys = new ArrayList<>();
        if (loc == LOC_DESKTOP) { for (DesktopItem it : DesktopStore.load()) keys.add(it.key); }
        else keys.addAll(DockStore.list());
        List<Folder> out = new ArrayList<>();
        for (Folder f : all()) if (f.placed() && keys.contains(PREFIX + f.id)) out.add(f);
        return out;
    }

    public static Folder find(String id) {
        for (Folder f : all()) if (f.id.equals(id)) return f;
        return null;
    }

    private static void saveAll(List<Folder> list) {
        List<String> lines = new ArrayList<>();
        for (Folder f : list) lines.add(f.id + ";" + f.name.replace(";", "").replace("\n", " ") + ";" + TextUtils.join(",", f.apps));
        Prefs.putString(Prefs.FOLDERS, TextUtils.join("\n", lines));
    }

    private static String newId(boolean placed) {
        return (placed ? PLACED_MARK : "") + System.currentTimeMillis() + (int) (Math.random() * 1000);
    }

    /** Нова папка меню програм. */
    public static String create(String name, List<String> initialApps) { return createIn(name, initialApps, false); }

    /** Нова папка для столу/дока (ключ потрібно одразу покласти у DesktopStore/DockStore). */
    public static String createPlaced(String name, List<String> initialApps) { return createIn(name, initialApps, true); }

    private static String createIn(String name, List<String> initialApps, boolean placed) {
        List<Folder> list = all();
        String id = newId(placed);
        while (find(id) != null) id = id + "0";
        Folder f = new Folder(id, name);
        f.apps.addAll(initialApps);
        list.add(f);
        saveAll(list);
        return id;
    }

    /** Незалежна копія папки (для столу/дока). Повертає id копії або null. */
    public static String copyToPlaced(String srcId) {
        Folder src = find(srcId);
        if (src == null) return null;
        return createPlaced(src.name, src.apps);
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

    /** Видаляє папку разом із її значком на столі/в доці (якщо це папка столу/дока). */
    public static void delete(String id) {
        List<Folder> list = all();
        List<Folder> keep = new ArrayList<>();
        for (Folder f : list) if (!f.id.equals(id)) keep.add(f);
        saveAll(keep);
        dropKeys(PREFIX + id);
    }

    private static void dropKeys(String folderKey) {
        List<DesktopItem> items = DesktopStore.load();
        List<DesktopItem> keepItems = new ArrayList<>();
        for (DesktopItem it : items) if (!it.key.equals(folderKey)) keepItems.add(it);
        if (keepItems.size() != items.size()) DesktopStore.save(keepItems);
        DockStore.remove(folderKey);
    }

    /**
     * Приводить сховища до єдиного стану (безпечно викликати скільки завгодно разів):
     *  - старі (до 0.7.3) спільні папки на столі/в доці стають незалежними копіями;
     *  - значки папок, яких уже не існує, прибираються зі столу й дока (раніше вони лишалися
     *    «примарами»: займали клітинку, не відкривалися);
     *  - папки столу/дока, на які ніщо не посилається, видаляються.
     */
    public static void migrate() {
        List<Folder> list = all();
        Map<String, Folder> byId = new HashMap<>();
        for (Folder f : list) byId.put(f.id, f);
        boolean listChanged = false;

        List<DesktopItem> items = DesktopStore.load();
        List<DesktopItem> newItems = new ArrayList<>();
        boolean desktopChanged = false;
        Set<String> used = new HashSet<>();
        for (DesktopItem it : items) {
            if (!isFolder(it.key)) { newItems.add(it); continue; }
            Folder f = byId.get(idOf(it.key));
            if (f == null) { desktopChanged = true; continue; }
            if (!f.placed() || used.contains(f.id)) {
                Folder c = new Folder(newId(true) + list.size(), f.name);
                c.apps.addAll(f.apps);
                list.add(c); byId.put(c.id, c); listChanged = true;
                newItems.add(new DesktopItem(PREFIX + c.id, it.col, it.row));
                used.add(c.id);
                desktopChanged = true;
            } else {
                used.add(f.id);
                newItems.add(it);
            }
        }

        List<String> dock = DockStore.list();
        List<String> newDock = new ArrayList<>();
        boolean dockChanged = false;
        for (String k : dock) {
            if (!isFolder(k)) { newDock.add(k); continue; }
            Folder f = byId.get(idOf(k));
            if (f == null) { dockChanged = true; continue; }
            if (!f.placed() || used.contains(f.id)) {
                Folder c = new Folder(newId(true) + list.size(), f.name);
                c.apps.addAll(f.apps);
                list.add(c); byId.put(c.id, c); listChanged = true;
                newDock.add(PREFIX + c.id);
                used.add(c.id);
                dockChanged = true;
            } else {
                used.add(f.id);
                newDock.add(k);
            }
        }

        // папки столу/дока без жодного значка — зайві
        List<Folder> keep = new ArrayList<>();
        for (Folder f : list) {
            if (f.placed() && !used.contains(f.id)) { listChanged = true; continue; }
            keep.add(f);
        }

        if (desktopChanged) DesktopStore.save(newItems);
        if (dockChanged) DockStore.saveOrder(newDock);
        if (listChanged) saveAll(keep);
    }

    /** Лише прибирання «осиротілих» папок столу/дока (після видалення значка). */
    public static void gc() { migrate(); }

    public static String defaultName() { return I18n.t("folder_default_name"); }
}
