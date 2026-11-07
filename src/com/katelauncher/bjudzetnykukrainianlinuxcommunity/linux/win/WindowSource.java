package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.graphics.Rect;

/** Джерело вікон: реальний Wayland-композитор або тестове. LinuxWm слухає події через Listener. */
public interface WindowSource {
    interface Listener {
        void onWindowAdded(LinuxWindow w);
        void onWindowRemoved(LinuxWindow w);
        void onWindowChanged(LinuxWindow w);                  // заголовок/розмір/підказки
        void onWindowDamage(LinuxWindow w, Rect r);            // змінена область буфера (логічні px)
        void onWindowRequest(LinuxWindow w, int kind, int arg); // Native.REQ_*
        void onPopupMoved(LinuxWindow w);
        void onClipboard(String text);                         // програма Linux щось скопіювала
        void onSourceGone(WindowSource s);
    }

    void setListener(Listener l);
    /** Логічний розмір «монітора», масштаб буфера, обмеження FPS. */
    void setOutput(int w, int h, int scale);
    void setFps(int fps);
    void setFocus(LinuxWindow w);          // null — жодного вікна не в фокусі
    void setGroup(int group);              // розкладка 0 — EN, 1 — УК
    void setClipboard(String text);
    /** Зупинити джерело (композитор завершиться сам). */
    void stop();
}
