package com.katelauncher.bjudzetnykukrainianlinuxcommunity.core;

import android.app.Application;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Theme.init();
        I18n.init(this);
    }
}
