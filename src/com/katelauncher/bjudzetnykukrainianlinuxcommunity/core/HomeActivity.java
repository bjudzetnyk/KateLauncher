package com.gnome.launcher.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gnome.launcher.apps.AppEntry;
import com.gnome.launcher.apps.AppLauncher;
import com.gnome.launcher.apps.AppRepository;
import com.gnome.launcher.apps.IconCache;
import com.gnome.launcher.desktop.DesktopItem;
import com.gnome.launcher.desktop.DesktopStore;
import com.gnome.launcher.desktop.DesktopView;
import com.gnome.launcher.dock.DockController;
import com.gnome.launcher.dock.DockHandleView;
import com.gnome.launcher.dock.DockOverlayService;
import com.gnome.launcher.dock.DockSide;
import com.gnome.launcher.dock.DockStore;
import com.gnome.launcher.dock.DockView;
import com.gnome.launcher.drawer.DrawerView;
import com.gnome.launcher.i18n.I18n;
import com.gnome.launcher.settings.SettingsActivity;
import com.gnome.launcher.ui.MiniMenu;
import com.gnome.launcher.widget.WidgetLayer;
import com.gnome.launcher.wallpaper.WallpaperView;

public class HomeActivity extends Activity implements DockView.Listener, DrawerView.Listener, DesktopView.Listener {
    private WallpaperView wallpaper;
    private DesktopView desktop;
    private WidgetLayer widgets;
    private DrawerView drawer;
    private DockView dock;
    private DockController dockCtl;
    private DockHandleView handle;
    private int dockW;

    private final FolderPopup.Listener folderListener = new FolderPopup.Listener() {
        @Override public void onLaunch(AppEntry e) { AppLauncher.launch(HomeActivity.this, e); }
        @Override public void onChanged() { desktop.reload(); dock.reload(); drawer.refresh(); }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(i.getAction()) && i.getData() != null) {
                String pkg = i.getData().getSchemeSpecificPart();
                DesktopStore.removePackage(pkg);
                DockStore.removePackage(pkg);
            }
            IconCache.clear();
            FolderIcon.clear();
            refreshApps();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SystemUi.apply(this);

        dockW = Ui.dp(this, Config.DOCK_WIDTH_DP);
        final int mp = ViewGroup.LayoutParams.MATCH_PARENT;

        FrameLayout root = new FrameLayout(this);
        wallpaper = new WallpaperView(this);
        desktop = new DesktopView(this);
        widgets = new WidgetLayer(this);
        drawer = new DrawerView(this);
        dock = new DockView(this);
        handle = new DockHandleView(this);

        root.addView(wallpaper, new FrameLayout.LayoutParams(mp, mp));
        root.addView(desktop, new FrameLayout.LayoutParams(mp, mp));
        root.addView(widgets, new FrameLayout.LayoutParams(mp, mp));
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(mp, mp));
        root.addView(dock, new FrameLayout.LayoutParams(dockW, mp, Gravity.LEFT));
        root.addView(handle, new FrameLayout.LayoutParams(
                Ui.dp(this, Config.HANDLE_W_DP), Ui.dp(this, Config.HANDLE_H_DP),
                Gravity.LEFT | Gravity.CENTER_VERTICAL));

        dockCtl = new DockController(dock, handle, desktop, widgets, drawer, dockW);
        dock.setController(dockCtl);
        handle.setController(dockCtl);
        dock.setListener(this);
        drawer.setListener(this);
        desktop.setListener(this);
        applySide();
        dockCtl.init(Prefs.getBool(Prefs.DOCK_HIDDEN, false));

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);

        wallpaper.reload();
        desktop.reload();
        widgets.reload();
        dock.reload();
        refreshApps();
        handleExtras(getIntent());
    }

    /** Ставить док (і ручку) на сторону, обрану для поточної орієнтації. */
    private void applySide() {
        int side = DockSide.current(this);
        boolean h = DockSide.horizontal(side);
        int mp = ViewGroup.LayoutParams.MATCH_PARENT;
        int g = DockSide.gravity(side);
        dock.setLayoutParams(new FrameLayout.LayoutParams(h ? mp : dockW, h ? dockW : mp, g));
        int hw = Ui.dp(this, Config.HANDLE_W_DP), hh = Ui.dp(this, Config.HANDLE_H_DP);
        handle.setLayoutParams(new FrameLayout.LayoutParams(h ? hh : hw, h ? hw : hh,
                g | (h ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL)));
        dock.setSide(side, DockSide.currentNavAtEnd(this));
        handle.setSide(side);
        dockCtl.setSide(side);
    }

    private boolean handleExtras(Intent i) {
        if (i == null) return false;
        if (i.getBooleanExtra("open_drawer", false)) {
            i.removeExtra("open_drawer");
            getWindow().getDecorView().post(new Runnable() { @Override public void run() { openDrawer(); } });
            return true;
        }
        final String f = i.getStringExtra("open_folder");
        if (f != null) {
            i.removeExtra("open_folder");
            getWindow().getDecorView().post(new Runnable() { @Override public void run() { FolderPopup.show(HomeActivity.this, f, folderListener); } });
            return true;
        }
        return false;
    }

    private void refreshApps() {
        AppRepository.refresh(this, new Runnable() {
            @Override
            public void run() {
                desktop.reload();
                dock.reload();
                drawer.refresh();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SystemUi.apply(this);
        if (wallpaper.needsReload()) wallpaper.reload();
        wallpaper.resume();
        desktop.reload();
        dock.reload();
        drawer.applyPrefs();
        applySide();
        DockOverlayService.sync(this);
        DockOverlayService.setSuppressed(true);
    }

    @Override
    protected void onStart() { super.onStart(); widgets.start(); }

    @Override
    protected void onStop() { widgets.stop(); super.onStop(); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (!widgets.handleResult(this, req, res, data)) super.onActivityResult(req, res, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        widgets.exitEdit();
        wallpaper.pause();
        DockOverlayService.setSuppressed(false);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) SystemUi.apply(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleExtras(intent)) return;
        if (Intent.ACTION_MAIN.equals(intent.getAction())) closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (widgets.isEditing()) widgets.exitEdit();
        else if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) { IconCache.clear(); FolderIcon.clear(); }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(pkgReceiver);
        super.onDestroy();
    }

    private void openDrawer() { drawer.open(); }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // активність сама обробляє поворот (configChanges у маніфесті), тож без цього
        // сітка і віджети просто розтягувались у нову орієнтацію замість переукладки по клітинках.
        if (dockCtl != null) applySide();
        if (desktop != null) desktop.reload();
        if (widgets != null) widgets.reload();
    }

    private void closeDrawer() { if (drawer.getVisibility() == View.VISIBLE) drawer.close(); }

    @Override
    public void onOpenDrawer() {
        if (drawer.getVisibility() == View.VISIBLE) closeDrawer(); else openDrawer();
    }

    @Override
    public void onOpenSettings() {
        closeDrawer();
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void renameFolderDialog(final String id) {
        final EditText input = new EditText(this);
        FolderStore.Folder f = FolderStore.find(id);
        if (f != null) input.setText(f.name);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("folder_rename"))
                .setView(input)
                .setPositiveButton(I18n.t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) FolderStore.rename(id, n);
                        desktop.reload(); dock.reload(); drawer.refresh();
                    }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }

    /** Спільне контекстне меню для елемента дока або робочого столу (програма чи папка). */
    private void showKeyMenu(final String key, View anchor, int x, int y, String removeLabel, final Runnable onRemove) {
        MiniMenu m = new MiniMenu(this);
        if (FolderStore.isFolder(key)) {
            final String id = FolderStore.idOf(key);
            m.add(I18n.t("folder_rename"), new Runnable() { @Override public void run() { renameFolderDialog(id); } });
            m.add(I18n.t("menu_add_dock"), new Runnable() {
                @Override public void run() {
                    if (DockStore.add(key)) dock.reload();
                    else Toast.makeText(HomeActivity.this, I18n.t("already_in_dock"), Toast.LENGTH_SHORT).show();
                }
            });
            m.add(I18n.t("menu_add_desktop"), new Runnable() {
                @Override public void run() {
                    if (DesktopStore.addKey(key)) desktop.reload();
                    else Toast.makeText(HomeActivity.this, I18n.t("desktop_full"), Toast.LENGTH_SHORT).show();
                }
            });
            m.add(I18n.t("folder_delete"), new Runnable() {
                @Override public void run() { FolderStore.delete(id); desktop.reload(); dock.reload(); drawer.refresh(); }
            });
        } else {
            final AppEntry e = AppRepository.find(key);
            if (e != null) {
                m.add(I18n.t("menu_app_info"), new Runnable() { @Override public void run() { AppLauncher.openInfo(HomeActivity.this, e.pkg); } });
                m.add(I18n.t("folder_add_to"), new Runnable() {
                    @Override public void run() {
                        FolderChooser.choose(HomeActivity.this, key, new FolderChooser.Done() {
                            @Override public void onDone() { desktop.reload(); dock.reload(); drawer.refresh(); }
                        });
                    }
                });
                m.add(I18n.t("menu_add_dock"), new Runnable() {
                    @Override public void run() {
                        if (DockStore.add(key)) dock.reload();
                        else Toast.makeText(HomeActivity.this, I18n.t("already_in_dock"), Toast.LENGTH_SHORT).show();
                    }
                });
                m.add(I18n.t("menu_add_desktop"), new Runnable() {
                    @Override public void run() {
                        if (DesktopStore.addKey(key)) desktop.reload();
                        else Toast.makeText(HomeActivity.this, I18n.t("desktop_full"), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        m.add(removeLabel, onRemove);
        m.showAt(anchor, x, y);
    }

    @Override
    public void onItemMenu(final String key, float rawX, float rawY) {
        int side = DockSide.current(this);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int gap = dockW + Ui.dp(this, 8);
        int mx = Math.round(rawX), my = Math.round(rawY);
        if (side == DockSide.LEFT) mx = gap;
        else if (side == DockSide.RIGHT) mx = dm.widthPixels - gap - Ui.dp(this, 240);
        else if (side == DockSide.TOP) my = gap;
        else my = my - Ui.dp(this, 240);
        showKeyMenu(key, dock, mx, my, I18n.t("menu_remove_dock"), new Runnable() {
            @Override public void run() { DockStore.remove(key); dock.reload(); }
        });
    }

    @Override
    public void onItemMenu(final DesktopItem item, float rawX, float rawY) {
        showKeyMenu(item.key, desktop, Math.round(rawX), Math.round(rawY), I18n.t("menu_remove_desktop"), new Runnable() {
            @Override public void run() { desktop.removeItem(item); }
        });
    }

    @Override
    public void onLaunch(AppEntry e) {
        AppLauncher.launch(this, e);
        closeDrawer();
    }

    @Override
    public void onMenu(final AppEntry e, View iconView) {
        int[] loc = new int[2];
        iconView.getLocationOnScreen(loc);
        new MiniMenu(this)
                .add(I18n.t("menu_app_info"), new Runnable() {
                    @Override public void run() { AppLauncher.openInfo(HomeActivity.this, e.pkg); }
                })
                .add(I18n.t("folder_add_to"), new Runnable() {
                    @Override public void run() {
                        FolderChooser.choose(HomeActivity.this, e.key, new FolderChooser.Done() {
                            @Override public void onDone() { drawer.refresh(); }
                        });
                    }
                })
                .add(I18n.t("menu_add_desktop"), new Runnable() {
                    @Override public void run() {
                        // навмисно НЕ закриваємо меню програм — зручно додавати відразу кілька
                        if (DesktopStore.addApp(e)) { desktop.reload(); Toast.makeText(HomeActivity.this, I18n.t("added_to_desktop"), Toast.LENGTH_SHORT).show(); }
                        else Toast.makeText(HomeActivity.this, I18n.t("desktop_full"), Toast.LENGTH_SHORT).show();
                    }
                })
                .add(I18n.t("menu_add_dock"), new Runnable() {
                    @Override public void run() {
                        if (DockStore.add(e.key)) dock.reload();
                        else Toast.makeText(HomeActivity.this, I18n.t("already_in_dock"), Toast.LENGTH_SHORT).show();
                    }
                })
                .showAt(iconView, loc[0] + iconView.getWidth() / 2, loc[1] + iconView.getHeight());
    }

    @Override
    public void onFolderAddToDock(String folderKey) {
        if (DockStore.add(folderKey)) dock.reload();
        else Toast.makeText(this, I18n.t("already_in_dock"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFolderAddToDesktop(String folderKey) {
        if (DesktopStore.addKey(folderKey)) desktop.reload();
        else Toast.makeText(this, I18n.t("desktop_full"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEmptyMenu(float rawX, float rawY) {
        new MiniMenu(this)
                .add(I18n.t("widget_pick"), new Runnable() {
                    @Override public void run() { widgets.pick(HomeActivity.this); }
                })
                .add(I18n.t("menu_set_wallpaper"), new Runnable() {
                    @Override public void run() {
                        Intent i = new Intent(HomeActivity.this, SettingsActivity.class);
                        i.putExtra("panel", "personal");
                        startActivity(i);
                    }
                })
                .add(I18n.t("settings"), new Runnable() {
                    @Override public void run() { startActivity(new Intent(HomeActivity.this, SettingsActivity.class)); }
                })
                .showAt(desktop, Math.round(rawX), Math.round(rawY));
    }

    @Override
    public void onOpenFolder(String folderId) { FolderPopup.show(this, folderId, folderListener); }

    @Override
    public void onDockChanged() { dock.reload(); }
}
