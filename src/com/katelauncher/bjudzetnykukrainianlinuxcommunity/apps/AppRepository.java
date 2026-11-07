package com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Список усіх застосунків із лаунчер-активністю. Завантажується у фоні. */
public final class AppRepository {
    private AppRepository() {}

    private static volatile List<AppEntry> apps = new ArrayList<>();
    private static volatile Map<String, AppEntry> byKey = new HashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean loaded;

    public static boolean isLoaded() { return loaded; }

    public static List<AppEntry> all() { return apps; }

    public static AppEntry find(String key) { return byKey.get(key); }

    private static volatile List<AppEntry> androidApps = new ArrayList<>();

    /** Перебудовує загальний список із системних програм і Linux-програм (без повторного опитування PackageManager). */
    public static void syncLinux() {
        List<AppEntry> list = new ArrayList<>(androidApps);
        Map<String, AppEntry> map = new HashMap<>();
        for (AppEntry e : list) map.put(e.key, e);
        for (AppEntry e : com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxApps.entries()) { list.add(e); map.put(e.key, e); }
        sort(list);
        apps = list; byKey = map;
    }

    private static void sort(List<AppEntry> list) {
        final Collator col = Collator.getInstance();
        Collections.sort(list, new java.util.Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return col.compare(a.label, b.label); }
        });
    }

    public static void refresh(Context ctx, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = app.getPackageManager();
                Intent q = new Intent(Intent.ACTION_MAIN);
                q.addCategory(Intent.CATEGORY_LAUNCHER);
                List<AppEntry> list = new ArrayList<>();
                Map<String, AppEntry> map = new HashMap<>();
                for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                    ActivityInfo ai = r.activityInfo;
                    if (ai.packageName.equals(app.getPackageName())) continue;
                    AppEntry e = new AppEntry(String.valueOf(r.loadLabel(pm)), ai.packageName, ai.name);
                    list.add(e);
                    map.put(e.key, e);
                }
                sort(list);
                androidApps = new ArrayList<>(list);
                for (AppEntry e : com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxApps.entries()) { list.add(e); map.put(e.key, e); }
                sort(list);
                apps = list;
                byKey = map;
                loaded = true;
                if (done != null) MAIN.post(done);
            }
        }, "apps-loader").start();
    }
}
