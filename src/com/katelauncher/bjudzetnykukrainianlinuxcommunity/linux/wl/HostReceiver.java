package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxPrefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win.LinuxWm;

/** Приймає Binder композитора, що стартував у Termux. Перевіряє токен. Не служба: живе лише на час onReceive. */
public final class HostReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent i) {
        try {
            Prefs.init(ctx);
            Bundle b = i.getBundleExtra("kwl");
            if (b == null) return;
            b.setClassLoader(getClass().getClassLoader());
            IBinder binder = b.getBinder("binder");
            String token = b.getString("token");
            if (binder == null || token == null || !token.equals(LinuxPrefs.token())) return;
            com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.SessionState.init(ctx);
            if (b.getInt("proto", 0) != Proto.VERSION) { WaylandSource.get().staleHost(); return; }   // хост старої збірки
            LinuxWm.init(ctx.getApplicationContext());
            WaylandSource.get().onHost(binder);
        } catch (Throwable ignored) {}
    }
}
