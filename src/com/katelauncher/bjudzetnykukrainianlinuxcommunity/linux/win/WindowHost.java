package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.graphics.Rect;

import java.util.List;

/** Де живуть рамки вікон: шар у HomeActivity (лише поверх столу) або системні overlay-вікна (поверх усіх програм). */
interface WindowHost {
    void add(WinFrame f);
    void remove(WinFrame f);
    /** Підняти рамку на верх z-порядку. */
    void raise(WinFrame f);
    /** Застосувати f.x, f.y, f.w, f.h. */
    void layout(WinFrame f);
    /** Доступна область (без дока й системних панелей) у координатах хоста. */
    Rect usable();
    /** Повна область хоста. */
    Rect bounds();
    /** Екранні координати початку хоста (для перерахунку rawX/rawY у координати хоста). */
    int[] origin();
    /** Підсвічування зон снапу. zones == null — сховати. */
    void showZones(List<Rect> zones, int active);
    /** Рамка стала/перестала фокусною для введення з клавіатури (потрібно для overlay). */
    void setInputFocus(WinFrame f, boolean focus);
    void destroy();
}
