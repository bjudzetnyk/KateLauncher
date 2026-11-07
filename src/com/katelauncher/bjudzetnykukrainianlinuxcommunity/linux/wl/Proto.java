package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

/** Коди Binder-транзакцій між композитором (Termux) і лаунчером. */
public final class Proto {
    private Proto() {}
    public static final int VERSION = 2;
    public static final String HOST_DESCRIPTOR = "katewl.host", SINK_DESCRIPTOR = "katewl.sink";

    // лаунчер -> композитор (oneway)
    public static final int H_ATTACH = 1;   // IBinder sink, int outW, outH, scale, fps, [idleMin, debug]
    public static final int H_CMD = 2;      // int op,a,b,c,d; double x,y; String s

    // композитор -> лаунчер (oneway)
    public static final int S_ADD = 1;      // id,kind,parent,px,py,w,h,scale, appTag,appId,title, decorated
    public static final int S_BUFFER = 2;   // id,gen,w,h,scale,size, ParcelFileDescriptor
    public static final int S_DAMAGE = 3;   // id,gen,x,y,w,h
    public static final int S_TITLE = 4;    // id,title
    public static final int S_APPID = 5;    // id,appId
    public static final int S_HINTS = 6;    // id,minw,minh,maxw,maxh
    public static final int S_REMOVE = 7;   // id
    public static final int S_REQUEST = 8;  // id,kind,arg
    public static final int S_POPUP = 9;    // id,px,py,w,h
    public static final int S_CLIPBOARD = 10; // text
    public static final int S_QUIT = 11;
    public static final int S_RUN = 12;     // runId, appId, state, code, pid, service, name
    public static final int S_LOG = 13;     // name, text
    public static final int S_LAST = 13;
}
