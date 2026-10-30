package com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;

/**
 * Налаштування положення дока. Для КОЖНОЇ орієнтації (книжкової й альбомної) є два незалежні вибори:
 *  1) «Положення дока» — Зліва / Справа / Зверху / Знизу;
 *  2) «Положення навігації» — де на доці стоять кнопки меню програм і налаштувань:
 *       док Зліва/Справа (вертикальний)  -> навігація Зверху або Знизу дока;
 *       док Зверху/Знизу (горизонтальний) -> навігація Зліва або Справа на доці.
 *
 * Навігація зберігається як «початок» (0: зверху / зліва) або «кінець» (1: знизу / справа) дока,
 * тому значення природно переноситься між вертикальним і горизонтальним доком.
 */
public final class DockSide {
    private DockSide() {}

    public static final int LEFT = 0, RIGHT = 1, TOP = 2, BOTTOM = 3;

    public static boolean isLandscape(Context c) {
        return c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    // ---- Положення дока ----

    public static int side(boolean land) {
        int v = Prefs.getInt(land ? Prefs.DOCK_POS_LAND : Prefs.DOCK_POS_PORT, LEFT);
        return v < 0 || v > 3 ? LEFT : v;
    }

    public static void setSide(boolean land, int side) {
        Prefs.putInt(land ? Prefs.DOCK_POS_LAND : Prefs.DOCK_POS_PORT, side);
    }

    // ---- Положення навігації на доці ----

    /** true — кнопки навігації в кінці дока (знизу / справа), false — на початку (зверху / зліва). */
    public static boolean navAtEnd(boolean land) {
        return Prefs.getInt(land ? Prefs.DOCK_NAV_LAND : Prefs.DOCK_NAV_PORT, 1) != 0;
    }

    public static void setNavAtEnd(boolean land, boolean end) {
        Prefs.putInt(land ? Prefs.DOCK_NAV_LAND : Prefs.DOCK_NAV_PORT, end ? 1 : 0);
    }

    // ---- Для поточної орієнтації екрана ----

    public static int current(Context c) { return side(isLandscape(c)); }

    public static boolean currentNavAtEnd(Context c) { return navAtEnd(isLandscape(c)); }

    public static boolean horizontal(int side) { return side >= TOP; }

    /** Знак зміщення по поперечній осі (екранні координати), яким док ховається. */
    public static int hideDir(int side) { return (side == LEFT || side == TOP) ? -1 : 1; }

    public static int gravity(int side) {
        switch (side) {
            case RIGHT: return Gravity.RIGHT;
            case TOP: return Gravity.TOP;
            case BOTTOM: return Gravity.BOTTOM;
            default: return Gravity.LEFT;
        }
    }
}
