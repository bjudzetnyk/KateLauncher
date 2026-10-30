package com.gnome.launcher.apps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

public final class AppLauncher {
    private AppLauncher() {}

    public static void launch(Context c, AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(e.cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {}
    }

    /** Відкриває СИСТЕМНЕ вікно «Інформація про програму». */
    public static void openInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {}
    }
}
