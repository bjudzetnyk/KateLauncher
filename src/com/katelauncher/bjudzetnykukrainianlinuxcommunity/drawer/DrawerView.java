package com.gnome.launcher.drawer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gnome.launcher.apps.AppEntry;
import com.gnome.launcher.apps.AppLauncher;
import com.gnome.launcher.apps.AppRepository;
import com.gnome.launcher.apps.IconCache;
import com.gnome.launcher.core.FolderIcon;
import com.gnome.launcher.core.FolderPopup;
import com.gnome.launcher.core.FolderStore;
import com.gnome.launcher.core.Prefs;
import com.gnome.launcher.core.Theme;
import com.gnome.launcher.core.Ui;
import com.gnome.launcher.i18n.I18n;
import com.gnome.launcher.ui.MiniMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DrawerView extends FrameLayout {
    public interface Listener {
        void onLaunch(AppEntry e);
        void onMenu(AppEntry e, View iconView);
        void onFolderAddToDock(String folderKey);
        void onFolderAddToDesktop(String folderKey);
    }

    private final LinearLayout top;
    private final GridView grid;
    private final EditText search;
    private final TextView count;
    private final AppAdapter adapter = new AppAdapter();
    private final List<Object> filtered = new ArrayList<>();
    private String query = "";
    private Listener listener;
    private int insL, insT, insR, insB;

    public DrawerView(Context c) {
        super(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);

        top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);

        search = new EditText(c);
        search.setHint(I18n.t("search_hint"));
        search.setHintTextColor(Theme.TEXT_DIM);
        search.setTextColor(Theme.TEXT);
        search.setSingleLine(true);
        search.setBackground(Ui.rounded(c, Theme.SURFACE, 24));
        search.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 10));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c2) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c2) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        top.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));

        count = Ui.text(c, "", 12, Theme.TEXT_DIM);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(c, 8);
        clp.leftMargin = Ui.dp(c, 4);
        top.addView(count, clp);

        grid = new GridView(c);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(c, 12));
        grid.setSelector(new ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(OVER_SCROLL_NEVER);
        grid.setAdapter(adapter);

        box.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        applyPrefs();
        setInsets(0, 0, 0, 0);
    }

    public void setListener(Listener l) { listener = l; }

    public void setInsets(int l, int t, int r, int b) {
        insL = l; insT = t; insR = r; insB = b;
        applyPadding();
    }

    private void applyPadding() {
        int p = Ui.dp(getContext(), 16);
        top.setPadding(insL + p, insT + Ui.dp(getContext(), 20), insR + p, Ui.dp(getContext(), 8));
        grid.setPadding(insL + p, p, insR + p, insB + p);
    }

    public void applyPrefs() {
        int alpha = Prefs.getInt(Prefs.DRAWER_BG_ALPHA, 242);
        int base = Theme.DRAWER_BG & 0x00FFFFFF;
        setBackgroundColor((alpha << 24) | base);
        int cell = 112;
        grid.setColumnWidth(Ui.dp(getContext(), cell));
        applyPadding();
        adapter.notifyDataSetChanged();
    }

    public void refresh() { filter(query); }

    private void filter(String q) {
        query = q;
        String needle = q.trim().toLowerCase(Locale.getDefault());
        filtered.clear();
        List<FolderStore.Folder> folders = FolderStore.all();
        java.util.Set<String> inFolders = new java.util.HashSet<>();
        for (FolderStore.Folder f : folders) inFolders.addAll(f.apps);
        for (FolderStore.Folder f : folders) {
            if (needle.isEmpty() || f.name.toLowerCase(Locale.getDefault()).contains(needle)) filtered.add(f);
        }
        for (AppEntry e : AppRepository.all()) {
            if (needle.isEmpty() && inFolders.contains(e.key)) continue; // поза пошуком — лише всередині своєї папки
            if (needle.isEmpty() || e.label.toLowerCase(Locale.getDefault()).contains(needle)) filtered.add(e);
        }
        count.setText(String.valueOf(filtered.size()));
        adapter.notifyDataSetChanged();
    }

    public void open() {
        animate().cancel();
        search.setText("");
        grid.setSelection(0);
        boolean anim = Prefs.getBool(Prefs.ANIM_ENABLED, true);
        setAlpha(anim ? 0f : 1f);
        setVisibility(VISIBLE);
        if (anim) animate().alpha(1f).setDuration(120).start();
    }

    public void close() {
        animate().cancel();
        if (!Prefs.getBool(Prefs.ANIM_ENABLED, true)) { setVisibility(GONE); return; }
        animate().alpha(0f).setDuration(100).withEndAction(new Runnable() {
            @Override public void run() { setVisibility(GONE); }
        }).start();
    }

    private void renameFolder(final String id) {
        final EditText input = new EditText(getContext());
        FolderStore.Folder f = FolderStore.find(id);
        if (f != null) input.setText(f.name);
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("folder_rename"))
                .setView(input)
                .setPositiveButton(I18n.t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) FolderStore.rename(id, n);
                        refresh();
                    }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }

    private void showFolderMenu(final FolderStore.Folder f, View anchor) {
        final String folderKey = FolderStore.PREFIX + f.id;
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        new MiniMenu(getContext())
                .add(I18n.t("folder_rename"), new Runnable() { @Override public void run() { renameFolder(f.id); } })
                .add(I18n.t("menu_add_dock"), new Runnable() { @Override public void run() { if (listener != null) listener.onFolderAddToDock(folderKey); } })
                .add(I18n.t("menu_add_desktop"), new Runnable() { @Override public void run() { if (listener != null) listener.onFolderAddToDesktop(folderKey); } })
                .add(I18n.t("folder_delete"), new Runnable() { @Override public void run() { FolderStore.delete(f.id); refresh(); } })
                .showAt(anchor, loc[0] + anchor.getWidth() / 2, loc[1] + anchor.getHeight());
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int i) { return filtered.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            AppCell cell = convert instanceof AppCell ? (AppCell) convert : new AppCell(parent.getContext());
            cell.bind(filtered.get(i));
            return cell;
        }
    }

    private final class AppCell extends LinearLayout {
        private final ImageView icon;
        private final TextView label;

        AppCell(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            int p = Ui.dp(c, 6);
            setPadding(p, p, p, p);
            icon = new ImageView(c);
            icon.setBackground(Ui.pressable(c, 0x00000000, 0x33FFFFFF, 12));
            addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
            label = Ui.text(c, "", 12, Theme.TEXT);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(c, 6);
            addView(label, lp);
        }

        void bind(Object item) {
            int px = Ui.dp(getContext(), Prefs.getInt(Prefs.DRAWER_ICON_SIZE, 56));
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            if (lp.width != px) { lp.width = px; lp.height = px; icon.setLayoutParams(lp); }

            if (item instanceof FolderStore.Folder) {
                final FolderStore.Folder f = (FolderStore.Folder) item;
                label.setText(f.name);
                final String fShape = Prefs.getString(Prefs.DRAWER_ICON_SHAPE, "round");
                final int fPx = px;
                final String fTag = "folder:" + f.id + "@" + fShape + "@" + px;
                icon.setTag(fTag);
                icon.setImageBitmap(FolderIcon.render(getContext(), f, px, fShape, new Runnable() {
                    @Override public void run() {
                        if (fTag.equals(icon.getTag())) icon.setImageBitmap(FolderIcon.render(getContext(), f, fPx, fShape, null));
                    }
                }));
                icon.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        FolderPopup.show(getContext(), f.id, new FolderPopup.Listener() {
                            @Override public void onLaunch(AppEntry e) { AppLauncher.launch(getContext(), e); }
                            @Override public void onChanged() { refresh(); }
                        });
                    }
                });
                icon.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { showFolderMenu(f, icon); return true; }
                });
                setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { icon.performClick(); } });
                return;
            }

            final AppEntry e = (AppEntry) item;
            label.setText(e.label);
            final String shape = Prefs.getString(Prefs.DRAWER_ICON_SHAPE, "round");
            final String tag = e.key + "@" + shape + "@" + px;
            icon.setTag(tag);
            Bitmap b = IconCache.peek(e, px, shape);
            if (b != null) {
                icon.setImageBitmap(b);
            } else {
                icon.setImageDrawable(null);
                IconCache.load(getContext(), e, px, shape, new IconCache.Callback() {
                    @Override public void onIcon(Bitmap bm) { if (tag.equals(icon.getTag())) icon.setImageBitmap(bm); }
                });
            }
            icon.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (listener != null) listener.onLaunch(e); } });
            icon.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { if (listener != null) listener.onMenu(e, icon); return true; } });
            setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (listener != null) listener.onLaunch(e); } });
        }
    }
}
