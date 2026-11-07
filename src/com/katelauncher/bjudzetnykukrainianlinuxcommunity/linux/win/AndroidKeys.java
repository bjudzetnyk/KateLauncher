package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.view.KeyEvent;
import android.util.SparseIntArray;

/** Android KeyEvent -> evdev (Linux) код клавіші. */
final class AndroidKeys {
    private AndroidKeys() {}
    private static final SparseIntArray M = new SparseIntArray();
    private static void p(int a, int e) { M.put(a, e); }
    static {
        String letters = "QWERTYUIOP";  int[] row1 = {16,17,18,19,20,21,22,23,24,25};
        for (int i = 0; i < 10; i++) p(KeyEvent.KEYCODE_A + (letters.charAt(i) - 'A'), row1[i]);
        String r2 = "ASDFGHJKL"; for (int i = 0; i < r2.length(); i++) p(KeyEvent.KEYCODE_A + (r2.charAt(i) - 'A'), 30 + i);
        String r3 = "ZXCVBNM"; for (int i = 0; i < r3.length(); i++) p(KeyEvent.KEYCODE_A + (r3.charAt(i) - 'A'), 44 + i);
        for (int i = 1; i <= 9; i++) p(KeyEvent.KEYCODE_0 + i, 1 + i);
        p(KeyEvent.KEYCODE_0, 11);
        p(KeyEvent.KEYCODE_ESCAPE, 1); p(KeyEvent.KEYCODE_DEL, 14); p(KeyEvent.KEYCODE_TAB, 15); p(KeyEvent.KEYCODE_ENTER, 28);
        p(KeyEvent.KEYCODE_NUMPAD_ENTER, 96); p(KeyEvent.KEYCODE_SPACE, 57); p(KeyEvent.KEYCODE_FORWARD_DEL, 111);
        p(KeyEvent.KEYCODE_CTRL_LEFT, 29); p(KeyEvent.KEYCODE_CTRL_RIGHT, 97); p(KeyEvent.KEYCODE_SHIFT_LEFT, 42); p(KeyEvent.KEYCODE_SHIFT_RIGHT, 54);
        p(KeyEvent.KEYCODE_ALT_LEFT, 56); p(KeyEvent.KEYCODE_ALT_RIGHT, 100); p(KeyEvent.KEYCODE_META_LEFT, 125); p(KeyEvent.KEYCODE_META_RIGHT, 126);
        p(KeyEvent.KEYCODE_CAPS_LOCK, 58); p(KeyEvent.KEYCODE_NUM_LOCK, 69); p(KeyEvent.KEYCODE_SCROLL_LOCK, 70);
        p(KeyEvent.KEYCODE_DPAD_UP, 103); p(KeyEvent.KEYCODE_DPAD_DOWN, 108); p(KeyEvent.KEYCODE_DPAD_LEFT, 105); p(KeyEvent.KEYCODE_DPAD_RIGHT, 106);
        p(KeyEvent.KEYCODE_MOVE_HOME, 102); p(KeyEvent.KEYCODE_MOVE_END, 107); p(KeyEvent.KEYCODE_PAGE_UP, 104); p(KeyEvent.KEYCODE_PAGE_DOWN, 109);
        p(KeyEvent.KEYCODE_INSERT, 110); p(KeyEvent.KEYCODE_MENU, 127); p(KeyEvent.KEYCODE_SYSRQ, 99);
        for (int i = 0; i < 10; i++) p(KeyEvent.KEYCODE_F1 + i, 59 + i);
        p(KeyEvent.KEYCODE_F11, 87); p(KeyEvent.KEYCODE_F12, 88);
        p(KeyEvent.KEYCODE_GRAVE, 41); p(KeyEvent.KEYCODE_MINUS, 12); p(KeyEvent.KEYCODE_EQUALS, 13);
        p(KeyEvent.KEYCODE_LEFT_BRACKET, 26); p(KeyEvent.KEYCODE_RIGHT_BRACKET, 27); p(KeyEvent.KEYCODE_BACKSLASH, 43);
        p(KeyEvent.KEYCODE_SEMICOLON, 39); p(KeyEvent.KEYCODE_APOSTROPHE, 40); p(KeyEvent.KEYCODE_COMMA, 51);
        p(KeyEvent.KEYCODE_PERIOD, 52); p(KeyEvent.KEYCODE_SLASH, 53);
        for (int i = 0; i <= 9; i++) p(KeyEvent.KEYCODE_NUMPAD_0 + i, new int[]{82,79,80,81,75,76,77,71,72,73}[i]);
        p(KeyEvent.KEYCODE_NUMPAD_ADD, 78); p(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, 74); p(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, 55);
        p(KeyEvent.KEYCODE_NUMPAD_DIVIDE, 98); p(KeyEvent.KEYCODE_NUMPAD_DOT, 83);
    }

    /** Код evdev або 0, якщо клавіші немає. Фізичні клавіатури дають готовий scanCode — він має пріоритет. */
    static int evdev(KeyEvent e) {
        int sc = e.getScanCode();
        if (sc > 0 && sc < 256 && e.getDevice() != null && !e.getDevice().isVirtual()) return sc;
        return M.get(e.getKeyCode(), 0);
    }

    static int evdev(int keyCode) { return M.get(keyCode, 0); }
}
