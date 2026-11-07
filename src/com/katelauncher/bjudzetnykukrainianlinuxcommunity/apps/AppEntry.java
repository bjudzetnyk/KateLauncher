package com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps;

import android.content.ComponentName;

public final class AppEntry {
    public final String label, pkg, cls, key;
    public final ComponentName cn;
    /** true — Linux-програма (ключ "linux/<id>"): запускається через Termux, а не Android-активність. */
    public final boolean linux;

    public AppEntry(String label, String pkg, String cls) { this(label, pkg, cls, false); }

    public AppEntry(String label, String pkg, String cls, boolean linux) {
        this.linux = linux;
        this.label = label;
        this.pkg = pkg;
        this.cls = cls;
        this.key = pkg + "/" + cls;
        this.cn = new ComponentName(pkg, cls);
    }
}
