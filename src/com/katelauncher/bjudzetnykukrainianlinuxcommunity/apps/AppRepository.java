package com.gnome.launcher.apps;

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

    public static List<AppEntry> all() { return apps; }

    public static AppEntry find(String key) { return byKey.get(key); }

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
                final Collator col = Collator.getInstance();
                Collections.sort(list, new java.util.Comparator<AppEntry>() {
                    @Override public int compare(AppEntry a, AppEntry b) { return col.compare(a.label, b.label); }
                });
                apps = list;
                byKey = map;
                if (done != null) MAIN.post(done);
            }
        }, "apps-loader").start();
    }
}
