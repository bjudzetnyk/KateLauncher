package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.LinuxBuffer;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.LinuxWindow;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.WindowSource;

/** Вікно Wayland-композитора. Усі дані оновлює WaylandSource (головний потік). */
final class WlWindow implements LinuxWindow {
    final WaylandSource src; final int id;
    int kind, parentId, px, py, w, h, sc = 1, rw, rh, gen, size, minW, minH, state;
    long ptr;
    String appKey, appId = "", title = "";
    boolean deco;
    LinuxBuffer buffer;

    WlWindow(WaylandSource s, int id) { this.src = s; this.id = id; }

    void release() {
        if (ptr != 0) { Native.nativeUnmap(ptr, size); ptr = 0; }
        if (buffer != null) { buffer.recycle(); buffer = null; }
    }

    @Override public int id() { return id; }
    @Override public String appKey() { return appKey; }
    @Override public String title() { return title != null && !title.isEmpty() ? title : appId; }
    @Override public int width() { return w; }
    @Override public int height() { return h; }
    @Override public int scale() { return sc; }
    @Override public LinuxBuffer buffer() { return buffer; }
    @Override public int state() { return state; }
    @Override public void setState(int s) { state = s; }
    @Override public boolean isPopup() { return kind == 1 || kind == 2; }
    @Override public boolean isForeignPopup() { return kind == 2; }
    @Override public LinuxWindow parent() { return parentId != 0 ? src.find(parentId) : null; }
    @Override public int popupX() { return px; }
    @Override public int popupY() { return py; }
    @Override public boolean serverDecorated() { return deco; }
    @Override public int minWidth() { return minW; }
    @Override public int minHeight() { return minH; }

    @Override public void requestSize(int nw, int nh, boolean maximized, boolean resizing) {
        src.cmd(Native.CMD_CONFIGURE, id, nw, nh, (maximized ? 1 : 0) | (resizing ? 4 : 0), 0, 0, null);
    }
    @Override public void requestClose() { src.cmd(Native.CMD_CLOSE, id, 0, 0, 0, 0, 0, null); }
    @Override public void setVisible(boolean v) { src.cmd(Native.CMD_VISIBLE, id, v ? 1 : 0, 0, 0, 0, 0, null); }
    @Override public void focus() { src.setFocus(this); }
    @Override public void dismissPopups() { src.cmd(Native.CMD_DISMISS_POPUPS, 0, 0, 0, 0, 0, 0, null); }
    @Override public void pointerMove(float x, float y) { src.cmd(Native.CMD_POINTER_MOVE, id, 0, 0, 0, x, y, null); }
    @Override public void pointerButton(int b, boolean down, float x, float y) { src.cmd(Native.CMD_POINTER_BUTTON, id, b, down ? 1 : 0, 0, x, y, null); }
    @Override public void pointerAxis(int axis, float v, int discrete) { src.cmd(Native.CMD_POINTER_AXIS, id, axis, discrete, 0, v, 0, null); }
    @Override public void pointerLeave() { src.cmd(Native.CMD_POINTER_LEAVE, 0, 0, 0, 0, 0, 0, null); }
    @Override public void key(int code, boolean down) { src.cmd(Native.CMD_KEY, code, down ? 1 : 0, 0, 0, 0, 0, null); }
    @Override public void setGroup(int g) { src.setGroup(g); }
    @Override public WindowSource source() { return src; }
}
