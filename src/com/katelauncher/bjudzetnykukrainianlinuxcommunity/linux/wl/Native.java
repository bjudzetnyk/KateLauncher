package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

/** JNI-міст до libkatewl.so (композитор у процесі Termux + копіювання кадрів у лаунчері). */
public final class Native {
    private Native() {}

    // Команди композитору (дзеркало KWL_CMD_* у kwl.h)
    public static final int CMD_POINTER_MOVE = 1, CMD_POINTER_BUTTON = 2, CMD_POINTER_AXIS = 3, CMD_POINTER_LEAVE = 4,
            CMD_KEY = 5, CMD_GROUP = 6, CMD_FOCUS = 7, CMD_CONFIGURE = 8, CMD_CLOSE = 9, CMD_VISIBLE = 10,
            CMD_OUTPUT = 11, CMD_FPS = 12, CMD_CLIPBOARD = 13, CMD_DISMISS_POPUPS = 14, CMD_STOP = 15,
            CMD_XWM = 16, CMD_XWM_STOP = 17, CMD_DEBUG = 18, CMD_IDLE = 19, CMD_ACTIVITY = 20, CMD_RESYNC = 21;
    // Команди менеджеру сеансу (обробляє Java-хост, а не C-композитор)
    public static final int SES_LAUNCH = 101, SES_KILL_APP = 102, SES_KILL_ALL = 103, SES_STOP = 104, SES_GET_LOG = 105,
            SES_LIST = 106, SES_IDLE = 107, SES_DEBUG = 108;
    // Запити клієнта (KWL_REQ_*)
    public static final int REQ_MOVE = 1, REQ_RESIZE = 2, REQ_MAXIMIZE = 3, REQ_MINIMIZE = 4, REQ_FULLSCREEN = 5;

    private static boolean loaded;

    /** Бібліотека потрібна й у процесі лаунчера (nativeMap/nativeBlit): вантажимо її при першому зверненні до класу. */
    static { load(); }

    /** Завантаження: у лаунчері — звичайно, у app_process — з APK (lib/<abi>/) або з KATE_LIB. */
    public static synchronized boolean load() {
        if (loaded) return true;
        try { System.loadLibrary("katewl"); loaded = true; return true; } catch (Throwable ignored) {}
        try {
            String abi = android.os.Build.SUPPORTED_ABIS[0];
            ClassLoader cl = Native.class.getClassLoader();
            java.net.URL u = cl != null ? cl.getResource("lib/" + abi + "/libkatewl.so") : null;
            if (u != null) { System.load(u.getFile().replace("file:", "")); loaded = true; return true; }
        } catch (Throwable t) { System.err.println("katewl: load from apk failed: " + t); }
        try {
            String d = System.getenv("KATE_LIB");
            if (d != null) { System.load(d + "/libkatewl.so"); loaded = true; return true; }
        } catch (Throwable t) { System.err.println("katewl: load from KATE_LIB failed: " + t); }
        return false;
    }

    // --- процес композитора ---
    public static native boolean nativeStart(String dir, String sock, String keymap, int w, int h, int scale, int fps);
    public static native void nativeCmd(int op, int a, int b, int c, int d, double x, double y, String s);

    // --- процес лаунчера ---
    public static native long nativeMap(int fd, int size);
    public static native void nativeUnmap(long ptr, int size);
    public static native boolean nativeBlit(long src, int srcStridePx, int sx, int sy, int w, int h, android.graphics.Bitmap dst, int dx, int dy);

    public static void cmd(int op, int a, int b, int c, int d, double x, double y, String s) { nativeCmd(op, a, b, c, d, x, y, s); }
}
