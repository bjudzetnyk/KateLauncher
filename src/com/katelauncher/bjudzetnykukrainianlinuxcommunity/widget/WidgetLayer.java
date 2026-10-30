package com.katelauncher.bjudzetnykukrainianlinuxcommunity.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.desktop.DesktopItem;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.desktop.DesktopStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.ui.MiniMenu;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Шар віджетів над робочим столом: сітка клітинок (ті самі cellW/cellH, що й у DesktopView),
 * звичайні AppWidget-и та гіфки-віджети. Позиція за замовчуванням спільна для обох орієнтацій
 * (просто вписується у поточну сітку); окрема фіксація — через контекстне меню на віджеті.
 */
public final class WidgetLayer extends FrameLayout {
    private static final int HOST_ID = 1024, REQ_BIND = 7001, REQ_CONFIG = 7002, REQ_GIF = 7003;

    private final AppWidgetHost host;
    private final AppWidgetManager mgr;
    private final List<WidgetStore.Entry> entries = new ArrayList<>();
    private Frame editing;
    private boolean swallow, running = true, landscape;
    private int pendingId = -1;
    private AppWidgetProviderInfo pendingInfo;
    private float insL, insT, insR, insB;
    private int cols = 6, rows = 5;
    private float cellW = 1, cellH = 1;

    public WidgetLayer(Context c) {
        super(c);
        host = new AppWidgetHost(c.getApplicationContext(), HOST_ID);
        mgr = AppWidgetManager.getInstance(c);
    }

    public void start() { running = true; try { host.startListening(); } catch (Exception ignored) {} setGifsRunning(true); }
    public void stop() { exitEdit(); running = false; try { host.stopListening(); } catch (Exception ignored) {} setGifsRunning(false); }
    public boolean isEditing() { return editing != null; }

    public void setInsets(float l, float t, float r, float b) {
        insL = l; insT = t; insR = r; insB = b;
        recalcGrid();
        relayoutAll();
    }

    private void setGifsRunning(boolean r) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof Frame && ((Frame) v).gifView != null) ((Frame) v).gifView.setRunning(r);
        }
    }

    public void reload() {
        removeAllViews();
        editing = null;
        entries.clear();
        landscape = WidgetStore.isLandscape(getContext());
        recalcGrid();
        for (WidgetStore.Entry e : WidgetStore.load(getContext())) {
            if (e.gif) {
                File f = gifFile(e.path);
                if (!f.exists()) continue;
                entries.add(e);
                addGifFrame(e, f);
            } else {
                AppWidgetProviderInfo info = null;
                try { info = mgr.getAppWidgetInfo(e.id); } catch (Exception ignored) {}
                if (info == null) { try { host.deleteAppWidgetId(e.id); } catch (Exception ignored) {} continue; }
                entries.add(e);
                addFrame(e, info);
            }
        }
    }

    private void recalcGrid() {
        int cellPx = Ui.dp(getContext(), 72);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || cellPx <= 0) return;
        float aw = w - insL - insR, ah = h - insT - insB;
        cols = Math.max(3, (int) (aw / cellPx));
        rows = Math.max(3, (int) (ah / cellPx));
        cellW = Math.max(1f, aw / cols);
        cellH = Math.max(1f, ah / (float) rows);
    }

    private void relayoutAll() {
        landscape = WidgetStore.isLandscape(getContext());
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof Frame) {
                Frame f = (Frame) v;
                f.loadFromEntry();
                v.setLayoutParams(params(f.vCol, f.vRow, f.vW, f.vH));
                f.sendSize();
            }
        }
    }

    private void addFrame(WidgetStore.Entry e, AppWidgetProviderInfo info) {
        try {
            AppWidgetHostView hv = host.createView(getContext().getApplicationContext(), e.id, info);
            Frame f = new Frame(getContext(), e, hv, null);
            addView(f, params(f.vCol, f.vRow, f.vW, f.vH));
        } catch (Exception ex) { /* пошкоджений віджет пропускаємо */ }
    }

    private void addGifFrame(WidgetStore.Entry e, File file) {
        GifView gv = new GifView(getContext(), file);
        gv.setRunning(running);
        Frame f = new Frame(getContext(), e, null, gv);
        addView(f, params(f.vCol, f.vRow, f.vW, f.vH));
    }

    private LayoutParams params(int col, int row, int w, int h) {
        LayoutParams lp = new LayoutParams(Math.round(w * cellW), Math.round(h * cellH));
        lp.leftMargin = Math.round(insL + col * cellW);
        lp.topMargin = Math.round(insT + row * cellH);
        return lp;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        recalcGrid();
        relayoutAll();
    }

    /** Вписує v* у поточні межі сітки (лише для показу, у сховище нічого не пишеться). */
    private void clampView(Frame f) {
        f.vW = Math.max(1, Math.min(f.vW, cols));
        f.vH = Math.max(1, Math.min(f.vH, rows));
        f.vCol = Math.max(0, Math.min(f.vCol, cols - f.vW));
        f.vRow = Math.max(0, Math.min(f.vRow, rows - f.vH));
    }

    private File gifDir() { File d = new File(getContext().getFilesDir(), "widget_gifs"); d.mkdirs(); return d; }
    private File gifFile(String name) { return new File(gifDir(), name); }

    // ---------- Режим редагування ----------

    void enterEdit(Frame f) {
        if (editing == f) return;
        exitEdit();
        editing = f;
        f.showOverlay(true);
        f.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    public void exitEdit() {
        if (editing == null) return;
        editing.showOverlay(false);
        editing = null;
    }

    private void remove(Frame f) {
        if (!f.e.gif) { try { host.deleteAppWidgetId(f.e.id); } catch (Exception ignored) {} }
        else { File gf = gifFile(f.e.path); if (gf.exists()) gf.delete(); }
        entries.remove(f.e);
        removeView(f);
        editing = null;
        WidgetStore.save(getContext(), entries);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN && editing != null) { exitEdit(); swallow = true; return true; }
        if (swallow) { if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) swallow = false; return true; }
        return false;
    }

    // ---------- Вільне місце (враховує й інші віджети, і іконки стола) ----------

    private boolean iconAt(int col, int row) {
        for (DesktopItem it : DesktopStore.load()) if (it.col == col && it.row == row) return true;
        return false;
    }

    private int[] freeSlot(int w, int h) {
        for (int r = 0; r <= rows - h; r++) {
            for (int c = 0; c <= cols - w; c++) {
                boolean ok = true;
                outer:
                for (int cc = c; cc < c + w; cc++) for (int rr = r; rr < r + h; rr++) {
                    if (WidgetStore.occupied(entries, landscape, cc, rr, null) || iconAt(cc, rr)) { ok = false; break outer; }
                }
                if (ok) return new int[]{c, r};
            }
        }
        return new int[]{0, 0};
    }

    // ---------- Додавання: вибір звичайного віджета або гіфки ----------

    public void pick(final Activity a) {
        final List<AppWidgetProviderInfo> list = new ArrayList<>();
        try { list.addAll(mgr.getInstalledProviders()); } catch (Exception ignored) {}
        final PackageManager pm = a.getPackageManager();
        final List<Object[]> rows2 = new ArrayList<>(); // {назва, info}
        for (AppWidgetProviderInfo i : list) {
            String app;
            try { app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(i.provider.getPackageName(), 0))); } catch (Exception e) { app = i.provider.getPackageName(); }
            String lbl;
            try { lbl = i.loadLabel(pm); } catch (Exception e) { lbl = app; }
            rows2.add(new Object[]{app + " — " + lbl, i});
        }
        Collections.sort(rows2, new Comparator<Object[]>() {
            @Override public int compare(Object[] x, Object[] y) { return ((String) x[0]).compareToIgnoreCase((String) y[0]); }
        });

        final List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{I18n.t("widget_gif_add"), null});
        rows.addAll(rows2);

        ListView lv = new ListView(a);
        lv.setAdapter(new ArrayAdapter<Object[]>(a, 0, rows) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert : row(a);
                Object[] it = rows.get(pos);
                TextView t = (TextView) row.getChildAt(1);
                ImageView icon = (ImageView) row.getChildAt(0);
                t.setText((String) it[0]);
                if (it[1] == null) { icon.setImageDrawable(null); icon.setBackgroundColor(0x33FFFFFF); }
                else {
                    AppWidgetProviderInfo info = (AppWidgetProviderInfo) it[1];
                    icon.setBackground(null);
                    Drawable d = previewOf(a, pm, info);
                    icon.setImageDrawable(d);
                }
                return row;
            }
        });
        final AlertDialog[] holder = new AlertDialog[1];
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (holder[0] != null) holder[0].dismiss();
                Object[] it = rows.get(pos);
                if (it[1] == null) pickGif(a); else add(a, (AppWidgetProviderInfo) it[1]);
            }
        });
        holder[0] = new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("widget_pick"))
                .setView(lv)
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }

    private Drawable previewOf(Activity a, PackageManager pm, AppWidgetProviderInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Drawable d = info.loadPreviewImage(a, 0);
                if (d != null) return d;
            }
            if (info.previewImage != 0) {
                return pm.getResourcesForApplication(info.provider.getPackageName()).getDrawable(info.previewImage, a.getTheme());
            }
        } catch (Exception ignored) {}
        try { return pm.getApplicationIcon(info.provider.getPackageName()); } catch (Exception ignored) {}
        return null;
    }

    private LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 10);
        l.setPadding(p, p, p, p);
        ImageView icon = new ImageView(c);
        l.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 44)));
        TextView t = Ui.text(c, "", 15, 0xFFFFFFFF);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Ui.dp(c, 12);
        l.addView(t, tp);
        return l;
    }

    private void add(Activity a, AppWidgetProviderInfo info) {
        try {
            pendingId = host.allocateAppWidgetId();
            pendingInfo = info;
            if (mgr.bindAppWidgetIdIfAllowed(pendingId, info.provider)) {
                configure(a);
            } else {
                Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                a.startActivityForResult(i, REQ_BIND);
            }
        } catch (Exception e) { fail(a); }
    }

    private void configure(Activity a) {
        if (pendingInfo != null && pendingInfo.configure != null) {
            try { host.startAppWidgetConfigureActivityForResult(a, pendingId, 0, REQ_CONFIG, null); return; }
            catch (Exception ignored) {}
        }
        finish(a);
    }

    private void finish(Activity a) {
        if (pendingId < 0 || pendingInfo == null) return;
        AppWidgetProviderInfo info = pendingInfo;
        recalcGrid();
        float cellPx = Ui.dp(getContext(), 72);
        int w = Math.max(1, Math.round(info.minWidth / Math.max(1f, cellPx)));
        int h = Math.max(1, Math.round(info.minHeight / Math.max(1f, cellPx)));
        w = Math.min(w, cols); h = Math.min(h, rows);
        int[] slot = freeSlot(w, h);
        WidgetStore.Entry e = new WidgetStore.Entry(pendingId, slot[0], slot[1], w, h);
        pendingId = -1; pendingInfo = null;
        entries.add(e);
        addFrame(e, info);
        WidgetStore.save(getContext(), entries);
        View last = getChildAt(getChildCount() - 1);
        if (last instanceof Frame) enterEdit((Frame) last);
    }

    private void fail(Activity a) {
        if (pendingId >= 0) { try { host.deleteAppWidgetId(pendingId); } catch (Exception ignored) {} }
        pendingId = -1; pendingInfo = null;
        Toast.makeText(a, I18n.t("widget_failed"), Toast.LENGTH_SHORT).show();
    }

    private void cancel() {
        if (pendingId >= 0) { try { host.deleteAppWidgetId(pendingId); } catch (Exception ignored) {} }
        pendingId = -1; pendingInfo = null;
    }

    // ---------- Гіфка-віджет ----------

    private void pickGif(Activity a) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/gif");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { a.startActivityForResult(i, REQ_GIF); }
        catch (Exception e) { Toast.makeText(a, I18n.t("widget_failed"), Toast.LENGTH_SHORT).show(); }
    }

    private void askGifSize(final Activity a, final String fileName) {
        final EditText input = new EditText(a);
        input.setHint("3x2");
        com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui.tintEdit(input);
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("widget_gif_size"))
                .setView(input)
                .setPositiveButton(I18n.t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int[] wh = parseWH(input.getText().toString());
                        finishGif(a, fileName, wh[0], wh[1]);
                    }
                })
                .setNegativeButton(I18n.t("cancel"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { gifFile(fileName).delete(); }
                })
                .show();
    }

    private int[] parseWH(String s) {
        try {
            String[] p = s.toLowerCase().split("x");
            int w = Math.max(1, Integer.parseInt(p[0].trim()));
            int h = Math.max(1, Integer.parseInt(p[1].trim()));
            return new int[]{w, h};
        } catch (Exception e) { return new int[]{3, 2}; }
    }

    private void finishGif(Activity a, String fileName, int w, int h) {
        recalcGrid();
        w = Math.min(w, cols); h = Math.min(h, rows);
        int[] slot = freeSlot(w, h);
        WidgetStore.Entry e = new WidgetStore.Entry(fileName, slot[0], slot[1], w, h);
        entries.add(e);
        addGifFrame(e, gifFile(fileName));
        WidgetStore.save(getContext(), entries);
        View last = getChildAt(getChildCount() - 1);
        if (last instanceof Frame) enterEdit((Frame) last);
    }

    /** Повертає true, якщо результат належав віджетам. */
    public boolean handleResult(Activity a, int req, int res, Intent data) {
        if (req == REQ_BIND) { if (res == Activity.RESULT_OK) configure(a); else cancel(); return true; }
        if (req == REQ_CONFIG) { if (res == Activity.RESULT_OK) finish(a); else cancel(); return true; }
        if (req == REQ_GIF) {
            if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
                String name = UUID.randomUUID().toString() + ".gif";
                try (InputStream in = a.getContentResolver().openInputStream(data.getData());
                     FileOutputStream out = new FileOutputStream(gifFile(name))) {
                    byte[] buf = new byte[8192]; int n;
                    while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                    askGifSize(a, name);
                } catch (Exception e) { Toast.makeText(a, I18n.t("widget_failed"), Toast.LENGTH_SHORT).show(); }
            }
            return true;
        }
        return false;
    }

    // ---------- Рамка віджета/гіфки ----------

    final class Frame extends FrameLayout {
        final WidgetStore.Entry e;
        final AppWidgetHostView hv;
        final GifView gifView;
        int vCol, vRow, vW, vH; // поточне відображуване положення (клітинки)
        private Overlay overlay;
        private boolean grab;
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private final Runnable lp = new Runnable() {
            @Override public void run() { grab = true; enterEdit(Frame.this); }
        };

        Frame(Context c, WidgetStore.Entry e, AppWidgetHostView hv, GifView gifView) {
            super(c);
            this.e = e;
            this.hv = hv;
            this.gifView = gifView;
            loadFromEntry();
            if (hv != null) addView(hv, new LayoutParams(-1, -1));
            else addView(gifView, new LayoutParams(-1, -1));
        }

        void loadFromEntry() {
            int[] v = e.effective(landscape);
            vCol = v[0]; vRow = v[1]; vW = v[2]; vH = v[3];
            clampView(this);
        }

        void sendSize() {
            if (hv == null) return;
            try { hv.updateAppWidgetSize(null, Math.round(vW * cellW), Math.round(vH * cellH), Math.round(vW * cellW), Math.round(vH * cellH)); } catch (Exception ignored) {}
        }

        void showOverlay(boolean on) {
            if (on && overlay == null) { overlay = new Overlay(getContext(), this); addView(overlay, new LayoutParams(-1, -1)); }
            else if (!on && overlay != null) { removeView(overlay); overlay = null; sendSize(); }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    grab = false;
                    if (editing != this) postDelayed(lp, ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lp);
                    break;
            }
            return grab;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) { return true; }
    }

    /** Рамка редагування: тягнути — рухати по клітинках, кут — розмір, × — видалити, ⋮ — зберегти позицію під орієнтацію. */
    final class Overlay extends View {
        private final Frame f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final int zone = Ui.dp(getContext(), 40);
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private int mode; // 0 рух, 1 розмір, 2 видалення, 3 меню позиції
        private float sx, sy;
        private int oc, orow, ow, oh;
        private boolean moved;

        Overlay(Context c, Frame f) { super(c); this.f = f; }

        @Override
        protected void onDraw(Canvas c) {
            float s = Ui.dp(getContext(), 2), w = getWidth(), h = getHeight(), rad = Ui.dp(getContext(), 13);
            p.setStyle(Paint.Style.FILL);
            p.setColor(com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme.accentAlpha(0x22));
            c.drawRect(0, 0, w, h, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            p.setColor(com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme.ACCENT);
            r.set(s / 2, s / 2, w - s / 2, h - s / 2);
            c.drawRect(r, p);
            // видалення — вгорі справа
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFD32F2F);
            c.drawCircle(w - zone / 2f, zone / 2f, rad, p);
            // розмір — внизу справа
            p.setColor(com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme.ACCENT);
            c.drawCircle(w - zone / 2f, h - zone / 2f, rad, p);
            // меню позиції — вгорі зліва
            p.setColor(0xFF4A4A4A);
            c.drawCircle(zone / 2f, zone / 2f, rad, p);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            float k = rad / 2.2f, cx = w - zone / 2f, cy = zone / 2f;
            c.drawLine(cx - k, cy - k, cx + k, cy + k, p);
            c.drawLine(cx - k, cy + k, cx + k, cy - k, p);
            cx = w - zone / 2f; cy = h - zone / 2f;
            c.drawLine(cx - k, cy + k, cx + k, cy - k, p);
            c.drawLine(cx, cy + k, cx + k, cy, p);
            c.drawLine(cx - k, cy, cx, cy - k, p);
            // три крапки меню
            p.setStyle(Paint.Style.FILL);
            for (int i = -1; i <= 1; i++) c.drawCircle(zone / 2f + i * (rad * 0.6f), zone / 2f, Ui.dp(getContext(), 2), p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    sx = ev.getRawX(); sy = ev.getRawY();
                    oc = f.vCol; orow = f.vRow; ow = f.vW; oh = f.vH;
                    moved = false;
                    float x = ev.getX(), y = ev.getY();
                    mode = (x > getWidth() - zone && y < zone) ? 2
                         : (x > getWidth() - zone && y > getHeight() - zone) ? 1
                         : (x < zone && y < zone) ? 3 : 0;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float mx = ev.getRawX() - sx, my = ev.getRawY() - sy;
                    if (Math.abs(mx) > slop || Math.abs(my) > slop) moved = true;
                    if (mode == 0) {
                        int dCol = Math.round(mx / cellW);
                        int dRow = Math.round(my / cellH);
                        int nc = Math.max(0, Math.min(cols - f.vW, oc + dCol));
                        int nr = Math.max(0, Math.min(rows - f.vH, orow + dRow));
                        if (nc != f.vCol || nr != f.vRow) {
                            boolean free = true;
                            outer:
                            for (int cc = nc; cc < nc + f.vW; cc++) for (int rr = nr; rr < nr + f.vH; rr++) {
                                if (WidgetStore.occupied(entries, landscape, cc, rr, f.e)) { free = false; break outer; }
                            }
                            if (free) {
                                f.vCol = nc; f.vRow = nr;
                                f.setLayoutParams(params(f.vCol, f.vRow, f.vW, f.vH)); // жорсткий стрибок по клітинках
                            }
                        }
                    } else if (mode == 1) {
                        int nw = Math.max(1, ow + Math.round(mx / cellW));
                        int nh = Math.max(1, oh + Math.round(my / cellH));
                        nw = Math.min(nw, cols - oc); nh = Math.min(nh, rows - orow);
                        f.vW = nw; f.vH = nh;
                        f.setLayoutParams(params(f.vCol, f.vRow, f.vW, f.vH));
                        f.sendSize();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (mode == 2) { remove(f); return true; }
                    if (mode == 3 && !moved) { showMenu(ev.getRawX(), ev.getRawY()); return true; }
                    clampView(f);
                    f.setLayoutParams(params(f.vCol, f.vRow, f.vW, f.vH));
                    f.sendSize();
                    f.e.apply(landscape, f.vCol, f.vRow, f.vW, f.vH);
                    WidgetStore.save(getContext(), entries);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    f.vCol = oc; f.vRow = orow; f.vW = ow; f.vH = oh;
                    f.setLayoutParams(params(f.vCol, f.vRow, f.vW, f.vH));
                    return true;
            }
            return true;
        }

        private void showMenu(float rawX, float rawY) {
            String label = I18n.t("widget_save_position") + ": " + (landscape ? I18n.t("orientation_land") : I18n.t("orientation_port"));
            new MiniMenu(getContext())
                    .add(label, new Runnable() {
                        @Override public void run() {
                            f.e.saveOverride(landscape, f.vCol, f.vRow, f.vW, f.vH);
                            WidgetStore.save(getContext(), entries);
                            Toast.makeText(getContext(), I18n.t("widget_position_saved"), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .showAt(this, Math.round(rawX), Math.round(rawY));
        }
    }
}
