package com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps;

import android.content.ComponentName;

public final class AppEntry {
    public final String label, pkg, cls, key;
    public final ComponentName cn;

    public AppEntry(String label, String pkg, String cls) {
        this.label = label;
        this.pkg = pkg;
        this.cls = cls;
        this.key = pkg + "/" + cls;
        this.cn = new ComponentName(pkg, cls);
    }
}
