package com.gnome.launcher.core;

import android.graphics.Path;

/** Форми для обрізки іконок: circle / round / square. */
public final class Shapes {
    private Shapes() {}

    public static Path path(String shape, float size) {
        Path p = new Path();
        if ("circle".equals(shape)) {
            p.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        } else if ("square".equals(shape)) {
            p.addRect(0, 0, size, size, Path.Direction.CW);
        } else {
            float r = size * 0.28f;
            p.addRoundRect(0, 0, size, size, r, r, Path.Direction.CW);
        }
        return p;
    }
}
