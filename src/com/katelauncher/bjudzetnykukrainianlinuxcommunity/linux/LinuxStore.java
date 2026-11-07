package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.FolderStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.desktop.DesktopStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockStore;

/** Сховище середовищ і програм (JSON у налаштуваннях). */
public final class LinuxStore {
    private LinuxStore() {}

    public static String newId(String prefix) {
        return prefix + Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
    }

    // ---- середовища ----
    public static List<LinuxEnv> envs() {
        List<LinuxEnv> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.getString(LinuxPrefs.ENVS, "[]"));
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); LinuxEnv e = new LinuxEnv(o.getString("id"), o.optString("name"), o.optString("cmd")); e.display = o.optString("disp"); l.add(e); }
        } catch (Exception ignored) {}
        return l;
    }

    private static void saveEnvs(List<LinuxEnv> l) {
        try {
            JSONArray a = new JSONArray();
            for (LinuxEnv e : l) a.put(new JSONObject().put("id", e.id).put("name", e.name).put("cmd", e.command).put("disp", e.display == null ? "" : e.display));
            Prefs.putString(LinuxPrefs.ENVS, a.toString());
        } catch (Exception ignored) {}
    }

    public static LinuxEnv env(String id) { for (LinuxEnv e : envs()) if (e.id.equals(id)) return e; return null; }

    public static LinuxEnv addEnv(String name, String command) {
        List<LinuxEnv> l = envs(); LinuxEnv e = new LinuxEnv(newId("e"), name, command); l.add(e); saveEnvs(l); return e;
    }

    public static void updateEnv(String id, String name, String command) { updateEnv(id, name, command, null); }

    /** display == null — не змінювати. */
    public static void updateEnv(String id, String name, String command, String display) {
        List<LinuxEnv> l = envs();
        for (LinuxEnv e : l) if (e.id.equals(id)) { e.name = name; e.command = command; if (display != null) e.display = display; }
        saveEnvs(l);
    }

    /** Видаляє середовище разом із його програмами. */
    public static void deleteEnv(android.content.Context c, String id) {
        for (LinuxApp a : apps()) if (a.envId.equals(id)) deleteApp(c, a.id);
        List<LinuxEnv> l = envs(), keep = new ArrayList<>();
        for (LinuxEnv e : l) if (!e.id.equals(id)) keep.add(e);
        saveEnvs(keep);
    }

    // ---- програми ----
    public static List<LinuxApp> apps() {
        List<LinuxApp> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.getString(LinuxPrefs.APPS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                LinuxApp x = new LinuxApp(o.getString("id"), o.optString("name"), o.optString("env"), o.optString("cmd"), o.optBoolean("icon"));
                x.display = o.optString("disp");
                l.add(x);
            }
        } catch (Exception ignored) {}
        return l;
    }

    private static void saveApps(List<LinuxApp> l) {
        try {
            JSONArray a = new JSONArray();
            for (LinuxApp x : l) a.put(new JSONObject().put("id", x.id).put("name", x.name).put("env", x.envId).put("cmd", x.command).put("icon", x.customIcon).put("disp", x.display == null ? "" : x.display));
            Prefs.putString(LinuxPrefs.APPS, a.toString());
        } catch (Exception ignored) {}
    }

    public static LinuxApp app(String id) { for (LinuxApp a : apps()) if (a.id.equals(id)) return a; return null; }

    public static LinuxApp addApp(String name, String envId, String command) {
        List<LinuxApp> l = apps(); LinuxApp a = new LinuxApp(newId("l"), name, envId, command, false); l.add(a); saveApps(l); return a;
    }

    /** Додає або замінює програму за id (id створюється наперед, щоб іконка з галереї мала ім'я файлу). */
    public static void putApp(LinuxApp upd) {
        List<LinuxApp> l = apps();
        boolean found = false;
        for (LinuxApp a : l) if (a.id.equals(upd.id)) { a.name = upd.name; a.envId = upd.envId; a.command = upd.command; a.customIcon = upd.customIcon; a.display = upd.display; found = true; }
        if (!found) l.add(upd);
        saveApps(l);
    }

    public static void updateApp(LinuxApp upd) {
        List<LinuxApp> l = apps();
        for (LinuxApp a : l) if (a.id.equals(upd.id)) { a.name = upd.name; a.envId = upd.envId; a.command = upd.command; a.customIcon = upd.customIcon; a.display = upd.display; }
        saveApps(l);
    }

    public static void deleteApp(android.content.Context c, String id) {
        List<LinuxApp> l = apps(), keep = new ArrayList<>();
        String key = LinuxApp.PKG + "/" + id;
        for (LinuxApp a : l) if (!a.id.equals(id)) keep.add(a);
        saveApps(keep);
        DesktopStore.removeKey(key);
        DockStore.remove(key);
        for (FolderStore.Folder f : FolderStore.all()) FolderStore.removeApp(f.id, key);
        LinuxIcons.delete(c, id);
        FolderStore.gc();
    }
}
