package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.SystemUi;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

/**
 * Налаштування в стилі «Параметрів» Ubuntu.
 * Вузький екран: список розділів -> сторінка розділу зі стрілкою «назад».
 * Широкий екран (>= 600dp): бічна панель із розділами ліворуч, вміст праворуч.
 */
public class SettingsActivity extends Activity {
    public interface ResultHandler {
        void onResult(int resultCode, Intent data);
    }

    private static final String STATE_PAGE = "page";

    private final SparseArray<ResultHandler> handlers = new SparseArray<>();
    private int nextCode = 100;

    private boolean wide;
    private Panels.Panel page; // null = список розділів (лише у вузькому режимі)

    private TextView title;
    private LinearLayout sidebar; // лише у широкому режимі
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SystemUi.apply(this);

        wide = SettingsUi.isWide(this);
        if (b != null) page = Panels.Panel.byId(b.getString(STATE_PAGE));
        if (page == null && getIntent() != null) page = Panels.Panel.byId(getIntent().getStringExtra("panel"));
        if (wide && page == null) page = Panels.Panel.SYSTEM;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SettingsUi.WINDOW);

        root.addView(buildHeader());
        root.addView(line(true), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 28));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content, new FrameLayout.LayoutParams(SettingsUi.contentWidth(this),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        if (wide) {
            LinearLayout body = new LinearLayout(this);
            sidebar = new LinearLayout(this);
            sidebar.setOrientation(LinearLayout.VERTICAL);
            sidebar.setBackgroundColor(SettingsUi.SIDEBAR);
            sidebar.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            ScrollView ss = new ScrollView(this);
            ss.addView(sidebar);
            body.addView(ss, new LinearLayout.LayoutParams(Ui.dp(this, SettingsUi.SIDEBAR_DP), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(line(false), new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        setContentView(root);
        rebuild();
    }

    private View line(boolean horizontal) {
        View v = new View(this);
        v.setBackgroundColor(SettingsUi.LINE);
        return v;
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SettingsUi.HEADER);
        bar.setMinimumHeight(Ui.dp(this, 52));
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);

        FrameLayout back = new FrameLayout(this);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, Ui.rounded(this, 0xFF000000, 10)));
        SymbolView arrow = new SymbolView(this, SymbolView.BACK, 0xFFFFFFFF);
        back.addView(arrow, new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 40)));

        title = Ui.text(this, "", 17, SettingsUi.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(wide ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        title.setSingleLine(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (wide) tp.leftMargin = Ui.dp(this, 10);
        bar.addView(title, tp);

        if (!wide) bar.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 44), 1)); // для центрування заголовка
        return bar;
    }

    /** Перебудовує екран (після зміни мови, шпалер тощо). */
    public void rebuild() {
        if (content == null) return;
        content.removeAllViews();

        if (wide) {
            title.setText(I18n.t("settings"));
            buildSidebar();
            addPanelTitle();
            Panels.build(this, page, content);
        } else if (page == null) {
            title.setText(I18n.t("settings"));
            buildPanelList();
        } else {
            title.setText(I18n.t(page.titleKey));
            Panels.build(this, page, content);
        }
    }

    private void addPanelTitle() {
        TextView t = Ui.text(this, I18n.t(page.titleKey), 22, SettingsUi.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), 0, Ui.dp(this, 10));
        content.addView(t);
    }

    private void open(Panels.Panel p) {
        page = p;
        rebuild();
    }

    /** Вузький режим: головний список розділів. */
    private void buildPanelList() {
        LinearLayout g = SettingsUi.group(this);
        for (final Panels.Panel p : Panels.Panel.values()) {
            SettingsUi.add(this, g, SettingsUi.navRow(this, p.symbol, I18n.t(p.titleKey), false, false, new Runnable() {
                @Override public void run() { open(p); }
            }));
        }
        content.addView(g);
    }

    /** Широкий режим: бічна панель. */
    private void buildSidebar() {
        sidebar.removeAllViews();
        for (Panels.Panel p : Panels.Panel.values()) addSidebarRow(p);
    }

    private void addSidebarRow(final Panels.Panel p) {
        sidebar.addView(SettingsUi.navRow(this, p.symbol, I18n.t(p.titleKey), true, p == page, new Runnable() {
            @Override public void run() { open(p); }
        }));
    }

    @Override
    public void onBackPressed() {
        if (!wide && page != null) { page = null; rebuild(); }
        else finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (page != null) out.putString(STATE_PAGE, page.id);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) SystemUi.apply(this);
    }

    @Override protected void onResume() {
        super.onResume();
        com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockOverlayService.setSuppressed(true);
        rebuild();
    }

    @Override protected void onPause() {
        com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockOverlayService.setSuppressed(false);
        super.onPause();
    }

    public void startForResult(Intent i, ResultHandler h) {
        int code = nextCode++;
        handlers.put(code, h);
        startActivityForResult(i, code);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (BackupDialog.handles(req)) { BackupDialog.onResult(this, req, res, data); return; }
        ResultHandler h = handlers.get(req);
        handlers.remove(req);
        if (h != null) h.onResult(res, data);
    }
}
