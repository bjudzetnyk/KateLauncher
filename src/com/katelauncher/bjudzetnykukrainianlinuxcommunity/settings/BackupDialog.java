package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

import java.util.HashSet;
import java.util.Set;

/**
 * Діалог вибору, які розділи персоналізації експортувати (за замовчуванням — усі), і
 * сам запуск експорту/імпорту через системний вибір файлу.
 */
final class BackupDialog {
    private BackupDialog() {}

    static void showExport(final SettingsActivity a) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 16);
        root.setPadding(pad, Ui.dp(a, 8), pad, 0);

        final CheckBox[] boxes = new CheckBox[BackupManager.CATEGORIES.length];
        for (int i = 0; i < BackupManager.CATEGORIES.length; i++) {
            BackupManager.Category cat = BackupManager.CATEGORIES[i];
            CheckBox cb = new CheckBox(a);
            cb.setText(I18n.t(cat.titleKey));
            cb.setTextColor(0xFFFFFFFF);
            cb.setChecked(true);
            boxes[i] = cb;
            root.addView(cb);
        }

        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("backup_export"))
                .setView(root)
                .setPositiveButton(I18n.t("backup_export"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Set<String> chosen = new HashSet<>();
                        for (int i = 0; i < boxes.length; i++) if (boxes[i].isChecked()) chosen.add(BackupManager.CATEGORIES[i].id);
                        if (chosen.isEmpty()) return;
                        pickDestination(a, chosen);
                    }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }

    static final int REQ_EXPORT = 9001, REQ_IMPORT = 9002;

    // Вибір файлу — окрема системна активність; на планшетах з малою пам'яттю (або при повороті)
    // SettingsActivity може перестворитися, поки відкрито вибір файлу. Тому результат обробляємо
    // за фіксованим кодом запиту, а обрані розділи тримаємо у статичному полі, а не в замиканні
    // на стару активність.
    private static Set<String> pendingExport;

    private static void pickDestination(final SettingsActivity a, final Set<String> chosen) {
        pendingExport = chosen;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, "KateLauncher_" + I18n.t("settings").toLowerCase() + ".klbackup");
        a.startActivityForResult(i, REQ_EXPORT);
    }

    static void showImport(final SettingsActivity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        a.startActivityForResult(i, REQ_IMPORT);
    }

    static boolean handles(int req) { return req == REQ_EXPORT || req == REQ_IMPORT; }

    private static String why(Throwable t) {
        String m = t.getMessage();
        String n = t.getClass().getSimpleName();
        if (m != null && m.length() > 60) m = m.substring(0, 60);
        return n + (m == null || m.isEmpty() ? "" : ": " + m);
    }

    static void onResult(final SettingsActivity a, int req, int res, Intent data) {
        if (res != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            Set<String> chosen = pendingExport;
            if (chosen == null || chosen.isEmpty()) return;
            try {
                BackupManager.export(a, uri, chosen);
                Toast.makeText(a, I18n.t("backup_exported"), Toast.LENGTH_SHORT).show();
            } catch (Throwable e) {
                Toast.makeText(a, I18n.t("backup_failed") + " (" + why(e) + ")", Toast.LENGTH_LONG).show();
            } finally {
                pendingExport = null;
            }
            return;
        }
        boolean imported = false;
        try {
            BackupManager.importFrom(a, uri);
            imported = true;
        } catch (Throwable e) {
            Toast.makeText(a, I18n.t("backup_failed") + " (" + why(e) + ")", Toast.LENGTH_LONG).show();
        }
        if (!imported) return;
        Toast.makeText(a, I18n.t("backup_imported"), Toast.LENGTH_SHORT).show();
        // Оновлення екрана налаштувань — окремо: збій перемальовування не має видаватися за збій імпорту.
        try { a.rebuild(); } catch (Throwable ignored) {}
    }
}
