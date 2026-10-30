package com.katelauncher.bjudzetnykukrainianlinuxcommunity.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * «Додати в папку»: список папок ТОГО Ж місця (меню програм / стіл / док) + створення нової.
 * Папки різних місць незалежні, тож програма додається лише в папку свого місця.
 */
public final class FolderChooser {
    private FolderChooser() {}

    public interface Done {
        /** createdFolderKey — ключ щойно створеної папки столу/дока (її треба поставити на місце),
         *  або null, якщо програму додали в наявну папку. */
        void onDone(String createdFolderKey);
    }

    public static void choose(final Context c, final String appKey, final int loc, final Done done) {
        final List<FolderStore.Folder> folders = FolderStore.foldersAt(loc);
        String[] labels = new String[folders.size() + 1];
        labels[0] = I18n.t("folder_new");
        for (int i = 0; i < folders.size(); i++) labels[i + 1] = folders.get(i).name;
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("folder_add_to"))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) createNew(c, appKey, loc, done);
                        else {
                            FolderStore.addApp(folders.get(which - 1).id, appKey);
                            if (done != null) done.onDone(null);
                        }
                    }
                }).show();
    }

    private static void createNew(final Context c, final String appKey, final int loc, final Done done) {
        final EditText input = new EditText(c);
        input.setHint(I18n.t("folder_default_name"));
        Ui.tintEdit(input);
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("folder_new"))
                .setView(input)
                .setPositiveButton(I18n.t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (n.isEmpty()) n = I18n.t("folder_default_name");
                        List<String> list = new ArrayList<>();
                        list.add(appKey);
                        if (loc == FolderStore.LOC_DRAWER) {
                            FolderStore.create(n, list);
                            if (done != null) done.onDone(null);
                        } else {
                            String id = FolderStore.createPlaced(n, list);
                            if (done != null) done.onDone(FolderStore.PREFIX + id);
                        }
                    }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }
}
