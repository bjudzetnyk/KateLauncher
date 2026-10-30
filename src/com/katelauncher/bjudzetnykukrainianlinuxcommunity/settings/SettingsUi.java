package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;

/** Будівельні блоки інтерфейсу налаштувань у стилі Ubuntu (Yaru dark / libadwaita). */
final class SettingsUi {
    private SettingsUi() {}

    // ---- Палітра ----
    static final int WINDOW = 0xFF242424;      // фон вікна
    static final int HEADER = 0xFF2E2E2E;      // шапка
    static final int SIDEBAR = 0xFF2A2A2A;     // бічна панель
    static final int CARD = 0xFF303030;        // згруповані списки
    static final int POPOVER = 0xFF383838;     // випадні списки
    static final int DIVIDER = 0xFF3D3D3D;
    static final int LINE = 0xFF151515;        // тонкі лінії між панелями
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFF9A9A9A;
    static final int ICON = 0xFFD8D8D8;

    interface OnBool { void on(boolean v); }
    interface OnInt { void on(int v); }
    interface Fmt { String f(int v); }

    /** Ширина колонки з вмістом (враховує бічну панель на широких екранах). */
    static int contentWidth(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        if (isWide(c)) w -= Ui.dp(c, SIDEBAR_DP + 1);
        return Math.min(Ui.dp(c, 640), w);
    }

    static final int SIDEBAR_DP = 270;

    static boolean isWide(Context c) { return c.getResources().getConfiguration().screenWidthDp >= 600; }

    // ---- Групи ----

    /** Заголовок групи (жирний, над списком). */
    static TextView groupTitle(Context c, String s) {
        TextView t = Ui.text(c, s, 14, 0xDEFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 8));
        return t;
    }

    /** Закруглений список («boxed list»): рядки всередині обрізаються по контуру. */
    static LinearLayout group(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(Ui.rounded(c, CARD, 12));
        l.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(c, 4);
        l.setLayoutParams(lp);
        return l;
    }

    static void divider(Context c, LinearLayout g) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        g.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
    }

    /** Додає рядок у групу, ставлячи роздільник між рядками. */
    static void add(Context c, LinearLayout g, View row) {
        if (g.getChildCount() > 0) divider(c, g);
        g.addView(row);
    }

    // ---- Рядки ----

    private static LinearLayout baseRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 54));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        return row;
    }

    private static void ripple(View v) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, new ColorDrawable(0xFF000000)));
    }

    private static LinearLayout titles(Context c, String title, String subtitle) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(c, title, 15, TEXT));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = Ui.text(c, subtitle, 13, TEXT_DIM);
            s.setPadding(0, Ui.dp(c, 1), 0, 0);
            col.addView(s);
        }
        return col;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static void addSymbol(Context c, LinearLayout row, int type, int color, int sizeDp, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp));
        lp.leftMargin = Ui.dp(c, leftMarginDp);
        row.addView(new SymbolView(c, type, color), lp);
    }

    /** Рядок-дія: заголовок, необов'язковий підпис і шеврон справа. */
    static View actionRow(Context c, String title, String subtitle, final Runnable onClick) {
        LinearLayout row = baseRow(c);
        row.addView(titles(c, title, subtitle), weight());
        addSymbol(c, row, SymbolView.CHEVRON_RIGHT, TEXT_DIM, 20, 8);
        ripple(row);
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return row;
    }

    /** Рядок-дія з червоним заголовком (без шеврона). */
    static View dangerRow(Context c, String title, final Runnable onClick) {
        LinearLayout row = baseRow(c);
        TextView t = Ui.text(c, title, 15, 0xFFFF4D4D);
        row.addView(t, weight());
        ripple(row);
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return row;
    }

    /** Рядок розділу: іконка + назва (+ шеврон у вузькому режимі, підсвічування у бічній панелі). */
    static View navRow(Context c, int sym, String title, boolean sidebar, boolean selected, final Runnable onClick) {
        LinearLayout row = baseRow(c);
        row.setMinimumHeight(Ui.dp(c, sidebar ? 46 : 54));
        addSymbol(c, row, sym, selected ? 0xFFFFFFFF : ICON, 22, 0);
        TextView t = Ui.text(c, title, 15, TEXT);
        if (selected) t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = weight();
        lp.leftMargin = Ui.dp(c, 14);
        row.addView(t, lp);
        if (!sidebar) addSymbol(c, row, SymbolView.CHEVRON_RIGHT, TEXT_DIM, 20, 8);
        if (sidebar) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
            row.setLayoutParams(rp);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), selected ? Ui.rounded(c, Theme.ACCENT, 8) : null, Ui.rounded(c, 0xFF000000, 8)));
        } else {
            ripple(row);
        }
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return row;
    }

    /** Рядок із перемикачем. */
    static View switchRow(Context c, String title, boolean on, final OnBool cb) {
        LinearLayout row = baseRow(c);
        row.addView(titles(c, title, null), weight());
        final GSwitch sw = new GSwitch(c, on);
        sw.setListener(new GSwitch.Listener() { @Override public void onChanged(boolean v) { cb.on(v); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(c, 12);
        row.addView(sw, lp);
        ripple(row);
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sw.toggle(); } });
        return row;
    }

    /** Рядок-вибір (як AdwComboRow): значення справа + стрілка, список відкривається поповером. */
    static View comboRow(final Context c, String title, final String[] labels, final int selected, final OnInt cb) {
        final LinearLayout row = baseRow(c);
        row.addView(titles(c, title, null), weight());
        TextView val = Ui.text(c, labels[selected], 14, TEXT_DIM);
        row.addView(val);
        addSymbol(c, row, SymbolView.CHEVRON_DOWN, TEXT_DIM, 20, 4);
        ripple(row);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { choosePopup(c, row, labels, selected, cb); }
        });
        return row;
    }

    /** Рядок із повзунком прямо в списку; значення зберігається, коли користувач відпустив повзунок. */
    static View sliderRow(Context c, String title, final int min, final int max, int cur, final Fmt fmt, final OnInt commit) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 6));

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.text(c, title, 15, TEXT), weight());
        final TextView val = Ui.text(c, fmt.f(cur), 14, TEXT_DIM);
        top.addView(val);
        row.addView(top);

        final SeekBar sb = new SeekBar(c);
        sb.setMax(max - min);
        sb.setProgress(Math.max(min, Math.min(max, cur)) - min);
        sb.setProgressTintList(ColorStateList.valueOf(Theme.ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(Theme.ACCENT));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF5A5A5A));
        sb.setSplitTrack(false);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { val.setText(fmt.f(min + p)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { commit.on(min + s.getProgress()); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 2);
        row.addView(sb, lp);
        return row;
    }

    // ---- Поповер вибору ----

    static void choosePopup(Context c, View anchor, String[] labels, int selected, final OnInt cb) {
        final PopupWindow[] holder = new PopupWindow[1];
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(c, POPOVER, 12));
        int vp = Ui.dp(c, 6);
        box.setPadding(0, vp, 0, vp);
        box.setMinimumWidth(Ui.dp(c, 200));
        box.setClipToOutline(true);

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(c);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setMinimumHeight(Ui.dp(c, 46));
            item.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 14), 0);
            item.addView(Ui.text(c, labels[i], 15, TEXT), weight());
            if (i == selected) addSymbol(c, item, SymbolView.CHECK, Theme.ACCENT, 20, 12);
            ripple(item);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    holder[0].dismiss();
                    cb.on(idx);
                }
            });
            box.addView(item);
        }

        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = box.getMeasuredWidth(), h = box.getMeasuredHeight();
        int m = Ui.dp(c, 8);
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int x = Math.max(m, Math.min(loc[0] + anchor.getWidth() - w - Ui.dp(c, 10), dm.widthPixels - w - m));
        int below = loc[1] + anchor.getHeight() - Ui.dp(c, 4);
        int y = below + h + m <= dm.heightPixels ? below : Math.max(m, loc[1] - h + Ui.dp(c, 4));

        PopupWindow pw = new PopupWindow(box, w, h, true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(Ui.dp(c, 12));
        holder[0] = pw;
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }
}
