package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

import java.util.ArrayList;
import java.util.List;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppEntry;

/** Міст між Linux-програмами і звичайним списком програм лаунчера (стіл, док, меню, папки, пошук). */
public final class LinuxApps {
    private LinuxApps() {}

    public static List<AppEntry> entries() {
        List<AppEntry> l = new ArrayList<>();
        for (LinuxApp a : LinuxStore.apps()) l.add(new AppEntry(a.name, LinuxApp.PKG, a.id, true));
        return l;
    }

    public static boolean isLinuxKey(String key) { return key != null && key.startsWith(LinuxApp.PKG + "/"); }
    public static String idOf(String key) { return key.substring(LinuxApp.PKG.length() + 1); }
}
