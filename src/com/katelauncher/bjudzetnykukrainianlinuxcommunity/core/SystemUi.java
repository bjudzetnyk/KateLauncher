package com.gnome.launcher.core;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/** Орієнтація екрана і приховування навігаційної панелі (імерсивний режим). */
public final class SystemUi {
    private SystemUi() {}

    public static void apply(Activity a) {
        int mode = Prefs.getInt(Prefs.ORIENTATION, 0);
        int req = mode == 1 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : mode == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (a.getRequestedOrientation() != req) a.setRequestedOrientation(req);

        if (a.getWindow().peekDecorView() == null) return; // декор ще не створено
        WindowInsetsController c = a.getWindow().getInsetsController();
        if (c == null) return; // вікно ще не прикріплене; виклик повториться у onWindowFocusChanged
        if (Prefs.getBool(Prefs.NAV_HIDDEN, false)) {
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsets.Type.systemBars());
        } else {
            c.show(WindowInsets.Type.systemBars());
        }
    }
}
