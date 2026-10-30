package com.gnome.launcher.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.gnome.launcher.core.Prefs;
import com.gnome.launcher.i18n.I18n;
import com.gnome.launcher.i18n.LanguageRepository;

import java.util.List;

/**
 * Вибір мови, імпорт стороннього файлу локалізації, "забування" імпортованої мови
 * (довге натискання на пункті), створення копії поточної локалізації для редагування.
 */
final class LanguageDialog {
    private LanguageDialog() {}

    static void show(final SettingsActivity a, final Runnable onChanged) {
        final List<LanguageRepository.Lang> langs = LanguageRepository.list(a);
        String[] names = new String[langs.size()];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            LanguageRepository.Lang l = langs.get(i);
            names[i] = l.name + "  (" + l.code + ")" + (l.user ? "  *" : "");
            if (l.code.equals(I18n.code())) checked = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("language"))
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        choose(a, langs.get(which).code);
                        onChanged.run();
                    }
                })
                .setPositiveButton(I18n.t("lang_import"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { importFile(a, onChanged); }
                })
                .setNeutralButton(I18n.t("lang_export"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { exportCopy(a); }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .create();
        dialog.show();
        ListView lv = dialog.getListView();
        if (lv != null) {
            lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override public boolean onItemLongClick(AdapterView<?> p, android.view.View v, int pos, long id) {
                    final LanguageRepository.Lang l = langs.get(pos);
                    if (!l.user) return false;
                    new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                            .setTitle(l.name)
                            .setMessage(I18n.t("lang_forget_confirm"))
                            .setPositiveButton(I18n.t("lang_forget"), new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d2, int w) {
                                    boolean wasCurrent = l.code.equals(I18n.code());
                                    LanguageRepository.forget(a, l.code);
                                    if (wasCurrent) choose(a, "");
                                    dialog.dismiss();
                                    Toast.makeText(a, I18n.t("lang_forgotten"), Toast.LENGTH_SHORT).show();
                                    onChanged.run();
                                    show(a, onChanged);
                                }
                            })
                            .setNegativeButton(I18n.t("cancel"), null)
                            .show();
                    return true;
                }
            });
        }
    }

    private static void choose(SettingsActivity a, String code) {
        Prefs.putString(Prefs.LANG, code);
        I18n.load(a, code);
    }

    private static void importFile(final SettingsActivity a, final Runnable onChanged) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        a.startForResult(i, new SettingsActivity.ResultHandler() {
            @Override public void onResult(int res, Intent data) {
                if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
                try {
                    String code = LanguageRepository.importFrom(a, data.getData());
                    choose(a, code);
                    Toast.makeText(a, I18n.t("lang_imported"), Toast.LENGTH_SHORT).show();
                    onChanged.run();
                } catch (Exception e) {
                    Toast.makeText(a, I18n.t("lang_import_failed"), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private static void exportCopy(final SettingsActivity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        a.startForResult(i, new SettingsActivity.ResultHandler() {
            @Override public void onResult(int res, Intent data) {
                if (res != Activity.RESULT_OK || data == null) return;
                Uri tree = data.getData();
                if (tree == null) return;
                try {
                    LanguageRepository.exportCurrent(a, tree);
                    Toast.makeText(a, I18n.t("lang_exported"), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(a, I18n.t("lang_export_failed"), Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
