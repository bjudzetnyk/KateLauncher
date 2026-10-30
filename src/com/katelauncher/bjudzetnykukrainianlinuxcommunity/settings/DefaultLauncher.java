package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.View;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

final class DefaultLauncher {
    private DefaultLauncher() {}

    static boolean isDefault(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    static void request(final SettingsActivity a) {
        if (!isDefault(a)) {
            try {
                RoleManager rm = a.getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    a.startForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), new SettingsActivity.ResultHandler() {
                        @Override public void onResult(int resultCode, Intent data) { a.rebuild(); }
                    });
                    return;
                }
            } catch (Exception ignored) {}
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    static View row(final SettingsActivity a) {
        String v = I18n.t(isDefault(a) ? "default_launcher_yes" : "default_launcher_no");
        return SettingsUi.actionRow(a, I18n.t("default_launcher"), v, new Runnable() {
            @Override public void run() { request(a); }
        });
    }
}
