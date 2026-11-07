package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;

/**
 * Рамка вікна: заголовок (перетягування, кнопки), поля для зміни розміру й WinContent усередині.
 * Координати x,y,w,h — у системі хоста (шар лаунчера або екран для overlay).
 */
final class WinFrame extends ViewGroup {
    static final int B_CLOSE = 1, B_MAX = 2, B_MIN = 3, B_LAYOUT = 4, B_KBD = 5, B_LANG = 6;
    private static final int E_T = 1, E_B = 2, E_L = 4, E_R = 8;
    private static final int R_CONTENT = 0, R_TITLE = 1, R_BTN = 2, R_EDGE = 3;

    final LinuxWm wm;
    final LinuxWindow win;
    final WinContent content;
    final boolean popup;
    int x, y, w, h;
    Rect restore;
    int zone = Snap.NONE;
    boolean focused, hidden, sizing;
    int prevState = LinuxWindow.STATE_NORMAL;

    private int m, tb;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int slop;
    // жест
    private int region, edges, btn;
    private float sx, sy, startRawX, startRawY;
    private int startX, startY, startW, startH;
    private boolean moved, dragging;
    private long lastTitleTap;
    private int extKind; private float extRawX, extRawY;

    WinFrame(Context c, LinuxWm wm, LinuxWindow win) {
        super(c);
        this.wm = wm; this.win = win; this.popup = win.isPopup();
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        content = new WinContent(c, this, win, wm);
        addView(content);
        setWillNotDraw(false);
        tp.setTextSize(Ui.sp(c, 13));
        refreshChrome();
    }

    // ---------------- Геометрія ----------------

    boolean showTitle() {
        if (popup) return false;
        if (win.serverDecorated()) return true;
        return com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.LinuxPrefs.csdMode() == 0;
    }

    void refreshChrome() {
        boolean max = win.state() == LinuxWindow.STATE_MAXIMIZED || zone != Snap.NONE;
        m = popup || max ? 0 : Ui.dp(getContext(), 7);
        tb = showTitle() ? Ui.dp(getContext(), 36) : 0;
    }

    int chromeW() { return 2 * m; }
    int chromeH() { return 2 * m + tb; }
    int contentW() { return Math.max(1, w - 2 * m); }
    int contentH() { return Math.max(1, h - 2 * m - tb); }
    int contentLeft() { return x + m; }
    int contentTop() { return y + m + tb; }

    void setContentSize(int cw, int ch) { refreshChrome(); w = cw + chromeW(); h = ch + chromeH(); }

    @Override protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(MeasureSpec.getSize(ws), MeasureSpec.getSize(hs));
        content.measure(MeasureSpec.makeMeasureSpec(Math.max(1, getMeasuredWidth() - 2 * m), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, getMeasuredHeight() - 2 * m - tb), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
        content.layout(m, m + tb, r - l - m, b - t - m);
    }

    // ---------------- Малювання ----------------

    private int[] buttons() {
        int avail = w - 2 * m;
        boolean wide = avail > Ui.dp(getContext(), 330), mid = avail > Ui.dp(getContext(), 230);
        if (wide) return new int[]{B_CLOSE, B_MAX, B_MIN, B_LAYOUT, B_KBD, B_LANG};
        if (mid) return new int[]{B_CLOSE, B_MAX, B_MIN, B_KBD};
        return new int[]{B_CLOSE, B_MIN};
    }

    @Override protected void onDraw(Canvas c) {
        if (popup) return;
        boolean saver = wm.saver();
        float d1 = Ui.dp(getContext(), 1);
        RectF body = new RectF(m, m, w - m, h - m);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1B1B1B);
        c.drawRect(body, p);
        if (tb > 0) {
            int base = focused ? 0xFF3A3A3A : 0xFF2B2B2B;
            p.setColor(saver ? base : (base & 0x00FFFFFF) | 0xF2000000);
            c.drawRect(m, m, w - m, m + tb, p);
            if (focused) { p.setColor(Theme.ACCENT); c.drawRect(m, m + tb - d1, w - m, m + tb, p); }
            int[] bs = buttons();
            tp.setTypeface(Typeface.DEFAULT);
            tp.setColor(focused ? Theme.TEXT : Theme.TEXT_DIM);
            float tx = m + Ui.dp(getContext(), 10), maxw = w - m - bs.length * tb - tx - Ui.dp(getContext(), 4);
            CharSequence t = TextUtils.ellipsize(win.title() == null ? "" : win.title(), new android.text.TextPaint(tp), Math.max(0, maxw), TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), tx, m + tb / 2f + tp.getTextSize() * 0.35f, tp);
            for (int i = 0; i < bs.length; i++) drawButton(c, bs[i], w - m - (i + 1) * tb, m);
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d1);
        p.setColor(focused ? Theme.ACCENT : 0xFF555555);
        c.drawRect(m + d1 / 2, m + d1 / 2, w - m - d1 / 2, h - m - d1 / 2, p);
    }

    private void drawButton(Canvas c, int id, float bx, float by) {
        float s = tb, cx = bx + s / 2, cy = by + s / 2, r = s * 0.17f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(getContext(), 1.6f)); p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(focused ? 0xFFFFFFFF : 0xFFB8B8B8);
        switch (id) {
        case B_CLOSE: c.drawLine(cx - r, cy - r, cx + r, cy + r, p); c.drawLine(cx - r, cy + r, cx + r, cy - r, p); break;
        case B_MIN: c.drawLine(cx - r, cy + r * 0.6f, cx + r, cy + r * 0.6f, p); break;
        case B_MAX:
            if (win.state() == LinuxWindow.STATE_MAXIMIZED) { c.drawRect(cx - r, cy - r * 0.4f, cx + r * 0.4f, cy + r, p); c.drawLine(cx - r * 0.4f, cy - r, cx + r, cy - r, p); c.drawLine(cx + r, cy - r, cx + r, cy + r * 0.4f, p); }
            else c.drawRect(cx - r, cy - r, cx + r, cy + r, p);
            break;
        case B_LAYOUT:
            c.drawRect(cx - r, cy - r, cx + r, cy + r, p); c.drawLine(cx, cy - r, cx, cy + r, p); c.drawLine(cx - r, cy, cx + r, cy, p); break;
        case B_KBD:
            c.drawRoundRect(new RectF(cx - r * 1.3f, cy - r * 0.8f, cx + r * 1.3f, cy + r * 0.8f), 3, 3, p);
            p.setStyle(Paint.Style.FILL); for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * r * 0.7f, cy - r * 0.15f, 1.5f, p);
            c.drawRect(cx - r * 0.7f, cy + r * 0.2f, cx + r * 0.7f, cy + r * 0.4f, p);
            break;
        case B_LANG:
            p.setStyle(Paint.Style.FILL);
            tp.setTypeface(Typeface.DEFAULT_BOLD); tp.setColor(wm.group() == 1 ? Theme.ACCENT : 0xFFFFFFFF);
            String t = wm.group() == 1 ? "\u0423\u041A" : "EN";
            c.drawText(t, cx - tp.measureText(t) / 2, cy + tp.getTextSize() * 0.35f, tp);
            tp.setTypeface(Typeface.DEFAULT);
            break;
        }
    }

    void setFocusedLook(boolean f) { if (focused != f) { focused = f; invalidate(); } }

    // ---------------- Дотики ----------------

    private int hit(float px, float py) {
        if (popup) return R_CONTENT;
        edges = 0; btn = 0;
        if (m > 0) {
            if (px < m || px > w - m || py < m || py > h - m) {
                int corner = (int) (m * 2.6f);
                if (px < corner) edges |= E_L;
                if (px > w - corner) edges |= E_R;
                if (py < corner) edges |= E_T;
                if (py > h - corner) edges |= E_B;
                return R_EDGE;
            }
        }
        if (tb > 0 && py < m + tb) {
            int[] bs = buttons();
            int idx = (int) ((w - m - px) / tb);
            if (px >= 0 && idx >= 0 && idx < bs.length && px <= w - m) { btn = bs[idx]; return R_BTN; }
            return R_TITLE;
        }
        return R_CONTENT;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        boolean r = super.dispatchTouchEvent(e);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) wm.flushRaise();
        return r;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            region = hit(e.getX(), e.getY());
            if (region != R_CONTENT) { wm.activate(this); return true; }
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float rx = e.getRawX(), ry = e.getRawY();
        switch (e.getActionMasked()) {
        case MotionEvent.ACTION_OUTSIDE:
            wm.onTouchOutside(rx, ry);
            return false;
        case MotionEvent.ACTION_DOWN:
            if (region == 0 && !hitCheck(e)) return false;
            sx = e.getX(); sy = e.getY(); startRawX = rx; startRawY = ry;
            startX = x; startY = y; startW = w; startH = h; moved = false; dragging = false;
            return true;
        case MotionEvent.ACTION_MOVE: {
            float dx = rx - startRawX, dy = ry - startRawY;
            if (!moved && Math.hypot(dx, dy) > slop) moved = true;
            if (!moved) return true;
            if (region == R_TITLE || (region == R_BTN && false)) {
                float[] hp = wm.toHost(rx, ry);
                if (!dragging) { dragging = true; beginDrag(hp[0], hp[1]); }
                dragTo(hp[0], hp[1]);
            } else if (region == R_EDGE) {
                resizeBy(edges, startX, startY, startW, startH, dx, dy, true);
            }
            return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (dragging) { float[] hp = wm.toHost(rx, ry); wm.endDrag(this, hp[0], hp[1]); }
                else if (region == R_EDGE && moved) { sizing = false; wm.finishResize(this); }
                else if (!moved) {
                    if (region == R_BTN) wm.onTitleButton(this, btn);
                    else if (region == R_TITLE) {
                        long t = android.os.SystemClock.uptimeMillis();
                        if (t - lastTitleTap < 350) { lastTitleTap = 0; wm.toggleMaximize(this); } else lastTitleTap = t;
                    }
                }
            } else if (dragging) wm.cancelDrag(this);
            dragging = false; moved = false; sizing = false;
            return true;
        }
        return true;
    }

    /** Курсор миші: усередині вікна — лаунчерний (замість системного), на краях — стрілки зміни розміру. */
    @Override public android.view.PointerIcon onResolvePointerIcon(MotionEvent e, int idx) {
        Context c = getContext();
        if (popup) return Cursors.arrow(c);
        int se = edges, sb = btn;
        int r = hit(e.getX(idx), e.getY(idx));
        int ed = edges; edges = se; btn = sb;
        if (r == R_EDGE) {
            boolean h = (ed & (E_L | E_R)) != 0, v = (ed & (E_T | E_B)) != 0;
            int t = h && v ? (((ed & E_L) != 0) == ((ed & E_T) != 0) ? android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW : android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
                    : h ? android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW : android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW;
            return android.view.PointerIcon.getSystemIcon(c, t);
        }
        return Cursors.arrow(c);
    }

    private boolean hitCheck(MotionEvent e) { region = hit(e.getX(), e.getY()); return region != R_CONTENT; }

    // ---------------- Перетягування ----------------

    private float offX, offY;

    void beginDrag(float hx, float hy) {
        if (win.state() == LinuxWindow.STATE_MAXIMIZED || zone != Snap.NONE) {
            float ratio = w > 0 ? (hx - x) / (float) w : 0.5f;
            wm.restoreFromSnap(this);
            x = Math.round(hx - ratio * w);
            y = Math.round(hy - Ui.dp(getContext(), 18));
        }
        offX = hx - x; offY = hy - y;
    }

    void dragTo(float hx, float hy) {
        x = Math.round(hx - offX); y = Math.round(hy - offY);
        wm.clampDrag(this);
        wm.moveFrame(this);
        wm.updateSnapPreview(this, hx, hy);
    }

    // ---------------- Зміна розміру ----------------

    void resizeBy(int edges, int x0, int y0, int w0, int h0, float dx, float dy, boolean live) {
        refreshChrome();
        float z = wm.zoom();
        int minW = Math.max(Ui.dp(getContext(), 200), Math.round(win.minWidth() * z)) + chromeW();
        int minH = Math.max(Ui.dp(getContext(), 120), Math.round(win.minHeight() * z)) + chromeH();
        int l = x0, t = y0, r = x0 + w0, b = y0 + h0;
        Rect bb = wm.bounds();
        if ((edges & E_L) != 0) l = Math.max(bb.left, Math.min(Math.round(x0 + dx), r - minW));
        if ((edges & E_R) != 0) r = Math.min(bb.right, Math.max(Math.round(x0 + w0 + dx), l + minW));
        if ((edges & E_T) != 0) t = Math.max(bb.top, Math.min(Math.round(y0 + dy), b - minH));
        if ((edges & E_B) != 0) b = Math.min(bb.bottom, Math.max(Math.round(y0 + h0 + dy), t + minH));
        sizing = true;
        wm.setBounds(this, l, t, r - l, b - t, live);
    }

    // ---------------- Запити програми (CSD) ----------------

    void extStart(int kind, int edgesFromXdg, float rawX, float rawY) {
        extKind = kind; startRawX = rawX; startRawY = rawY; startX = x; startY = y; startW = w; startH = h;
        if (kind == 2) {   // xdg: 1 top, 2 bottom, 4 left, 8 right
            edges = ((edgesFromXdg & 1) != 0 ? E_T : 0) | ((edgesFromXdg & 2) != 0 ? E_B : 0) | ((edgesFromXdg & 4) != 0 ? E_L : 0) | ((edgesFromXdg & 8) != 0 ? E_R : 0);
        } else { float[] hp = wm.toHost(rawX, rawY); dragging = true; beginDrag(hp[0], hp[1]); }
    }

    void extMove(float rawX, float rawY) {
        extRawX = rawX; extRawY = rawY;
        if (extKind == 1) { float[] hp = wm.toHost(rawX, rawY); dragTo(hp[0], hp[1]); }
        else if (extKind == 2) resizeBy(edges, startX, startY, startW, startH, rawX - startRawX, rawY - startRawY, true);
    }

    void extEnd() {
        if (extKind == 1) { dragging = false; float[] hp = wm.toHost(extRawX, extRawY); wm.endDrag(this, hp[0], hp[1]); }
        else { sizing = false; wm.finishResize(this); }
        extKind = 0;
    }
}
