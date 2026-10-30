package com.gnome.launcher.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

import com.gnome.launcher.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/** «Додати в папку»: список існуючих папок + створення нової. */
public final class FolderChooser {
    private FolderChooser() {}

    public interface Done { void onDone(); }

    public static void choose(final Context c, final String appKey, final Done done) {
        final List<FolderStore.Folder> folders = FolderStore.all();
        String[] labels = new String[folders.size() + 1];
        labels[0] = I18n.t("folder_new");
        for (int i = 0; i < folders.size(); i++) labels[i + 1] = folders.get(i).name;
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("folder_add_to"))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) createNew(c, appKey, done);
                        else {
                            FolderStore.addApp(folders.get(which - 1).id, appKey);
                            if (done != null) done.onDone();
                        }
                    }
                }).show();
    }

    private static void createNew(final Context c, final String appKey, final Done done) {
        final EditText input = new EditText(c);
        input.setHint(I18n.t("folder_default_name"));
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("folder_new"))
                .setView(input)
                .setPositiveButton(I18n.t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (n.isEmpty()) n = I18n.t("folder_default_name");
                        List<String> list = new ArrayList<>();
                        list.add(appKey);
                        FolderStore.create(n, list);
                        if (done != null) done.onDone();
                    }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }
}
