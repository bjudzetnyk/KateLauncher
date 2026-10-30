package com.gnome.launcher.dock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.gnome.launcher.apps.AppRepository;
import com.gnome.launcher.core.Config;
import com.gnome.launcher.core.HomeActivity;
import com.gnome.launcher.core.Prefs;
import com.gnome.launcher.core.Ui;
import com.gnome.launcher.i18n.I18n;
import com.gnome.launcher.settings.SettingsActivity;

/** Док поверх усіх програм (режим «док поверх всіх програм»). У самому лаунчері показується звичайний док. */
public class DockOverlayService extends Service {
    private static DockOverlayService inst;
    private static boolean suppressed;

    private WindowManager wm;
    private DockView dock;
    private DockHandleView handle;
    private DockController ctl;
    private boolean dockOn, handleOn;
    private int side = DockSide.LEFT;

    public static boolean wanted(Context c) {
        return Prefs.getInt(Prefs.DOCK_MODE, 0) == 1 && Settings.canDrawOverlays(c);
    }

    /** Запускає або зупиняє сервіс відповідно до налаштувань. */
    public static void sync(Context c) {
        Intent i = new Intent(c, DockOverlayService.class);
        if (wanted(c)) {
            try { c.startForegroundService(i); } catch (Exception ignored) {}
        } else {
            c.stopService(i);
        }
    }

    /** true, поки на екрані лаунчер або його налаштування (там показується власний док). */
    public static void setSuppressed(boolean s) {
        suppressed = s;
        if (inst != null) inst.refresh();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("dock_overlay", I18n.t("overlay_notif_title"), NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "dock_overlay")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle(I18n.t("overlay_notif_title"))
                .setOngoing(true)
                .build();
        startForeground(1, n);

        if (inst == null) {
            inst = this;
            wm = getSystemService(WindowManager.class);
            dock = new DockView(this);
            handle = new DockHandleView(this);
            dock.setOverlay(true);
            int size = Ui.dp(this, Config.DOCK_WIDTH_DP);
            ctl = new DockController(dock, handle, null, null, null, size);
            dock.setController(ctl);
            handle.setController(ctl);
            dock.setListener(new DockView.Listener() {
                @Override public void onOpenDrawer() { openHome("open_drawer", ""); }
                @Override public void onOpenSettings() {
                    Intent i = new Intent(DockOverlayService.this, SettingsActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
                @Override public void onItemMenu(String key, float rawX, float rawY) {}
                @Override public void onOpenFolder(String id) { openHome("open_folder", id); }
            });
            AppRepository.refresh(this, new Runnable() {
                @Override public void run() { if (dock != null) dock.reload(); }
            });
        }
        refresh();
        return START_STICKY;
    }

    private void openHome(String extra, String value) {
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (value.isEmpty()) i.putExtra(extra, true); else i.putExtra(extra, value);
        startActivity(i);
    }

    private void refresh() {
        if (wm == null || ctl == null) return;
        removeDock();
        removeHandle();
        if (suppressed || !wanted(this)) return;
        side = DockSide.current(this);
        ctl.setHost(null);
        ctl.setSide(side);
        dock.setSide(side, DockSide.currentNavAtEnd(this));
        handle.setSide(side);
        dock.reload();
        ctl.setHost(new DockController.Host() {
            @Override public void onShown(float f) { if (f > 0f) addDock(); }
            @Override public void onSettled(boolean hidden) {
                if (hidden) { removeDock(); addHandle(); } else removeHandle();
            }
        });
        ctl.init(Prefs.getBool(Prefs.DOCK_HIDDEN, false));
    }

    private void addDock() {
        if (dockOn) return;
        boolean h = DockSide.horizontal(side);
        int px = Ui.dp(this, Config.DOCK_WIDTH_DP);
        int mp = WindowManager.LayoutParams.MATCH_PARENT;
        WindowManager.LayoutParams lp = params(h ? mp : px, h ? px : mp);
        lp.gravity = DockSide.gravity(side);
        try { wm.addView(dock, lp); dockOn = true; } catch (Exception ignored) {}
    }

    private void addHandle() {
        if (handleOn) return;
        boolean h = DockSide.horizontal(side);
        int hw = Ui.dp(this, Config.HANDLE_W_DP), hh = Ui.dp(this, Config.HANDLE_H_DP);
        WindowManager.LayoutParams lp = params(h ? hh : hw, h ? hw : hh);
        lp.gravity = DockSide.gravity(side) | (h ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        try { wm.addView(handle, lp); handleOn = true; } catch (Exception ignored) {}
    }

    private WindowManager.LayoutParams params(int w, int h) {
        return new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
    }

    private void removeDock() {
        if (dockOn) { try { wm.removeView(dock); } catch (Exception ignored) {} dockOn = false; }
    }

    private void removeHandle() {
        if (handleOn) { try { wm.removeView(handle); } catch (Exception ignored) {} handleOn = false; }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        refresh();
    }

    @Override
    public void onDestroy() {
        removeDock();
        removeHandle();
        inst = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
