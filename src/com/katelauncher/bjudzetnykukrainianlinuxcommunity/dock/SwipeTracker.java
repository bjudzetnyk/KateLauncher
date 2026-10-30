package com.gnome.launcher.dock;

/** Швидкість свайпу по raw-координаті (View рухається, тому VelocityTracker не підходить). */
final class SwipeTracker {
    private float lastX, vx;
    private long lastT;

    void start(float rawX, long t) {
        lastX = rawX;
        lastT = t;
        vx = 0;
    }

    void move(float rawX, long t) {
        long dt = t - lastT;
        if (dt <= 0) return;
        float inst = (rawX - lastX) * 1000f / dt;
        vx = 0.5f * vx + 0.5f * inst;
        lastX = rawX;
        lastT = t;
    }

    float velocity() { return vx; }
}
