package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.KeyTable;

/**
 * Вміст вікна: малює плитки буфера й перетворює введення (миша, дотик-як-миша, клавіатура, IME)
 * на події Linux. Координати для програми — логічні: піксель екрана / zoom.
 */
final class WinContent extends View {
    private static final int BTN_LEFT = 0x110, BTN_RIGHT = 0x111, BTN_MID = 0x112;
    private static final int IDLE = 0, PENDING = 1, DRAG = 2, RIGHT_DONE = 3, SCROLL = 4, IGNORE = 5, EXT = 6;

    private final LinuxWindow win;
    private final WinFrame frame;
    private final LinuxWm wm;
    private final Paint paint = new Paint();
    private final int slop;
    private int mode = IDLE, prevButtons;
    private float downX, downY, lastY, lastX, lastRawX, lastRawY;
    private long lastMoveSent;
    boolean textEditor;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (mode != PENDING) return;
            mode = RIGHT_DONE;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            float lx = downX / z(), ly = downY / z();
            win.pointerButton(BTN_RIGHT, true, lx, ly);
            win.pointerButton(BTN_RIGHT, false, lx, ly);
        }
    };

    WinContent(Context c, WinFrame f, LinuxWindow w, LinuxWm wm) {
        super(c);
        frame = f; win = w; this.wm = wm;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);
    }

    private float z() { return wm.zoom(); }
    private float k() { LinuxBuffer b = win.buffer(); return z() / Math.max(1, b != null ? win.scale() : 1); }

    @Override protected void onDraw(Canvas c) {
        LinuxBuffer b = win.buffer();
        if (wm.saver() && !win.isPopup()) c.drawColor(0xFF1B1B1B);
        if (b == null) return;
        float k = k();
        paint.setFilterBitmap(Math.abs(k - 1f) > 0.01f);
        c.save();
        c.scale(k, k);
        b.draw(c, paint);
        c.restore();
    }

    /** Змінена область (у пікселях буфера). */
    void damage(Rect r) {
        float k = k();
        invalidate((int) Math.floor(r.left * k) - 1, (int) Math.floor(r.top * k) - 1, (int) Math.ceil(r.right * k) + 1, (int) Math.ceil(r.bottom * k) + 1);
    }

    // ---------------- Миша й дотик ----------------

    @Override public boolean onTouchEvent(MotionEvent e) { return handle(e); }

    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER || a == MotionEvent.ACTION_SCROLL
                || a == MotionEvent.ACTION_BUTTON_PRESS || a == MotionEvent.ACTION_BUTTON_RELEASE || a == MotionEvent.ACTION_HOVER_EXIT) return handle(e);
        return false;
    }

    private boolean handle(MotionEvent e) {
        lastRawX = e.getRawX(); lastRawY = e.getRawY();
        int a = e.getActionMasked();
        if (mode == EXT) {   // перетягування/зміна розміру за запитом самої програми (CSD)
            if (a == MotionEvent.ACTION_MOVE) frame.extMove(e.getRawX(), e.getRawY());
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { frame.extEnd(); mode = IDLE; }
            return true;
        }
        boolean mouse = e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE || (e.getSource() & android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE;
        return mouse ? mouse(e, a) : finger(e, a);
    }

    private void sendMove(float x, float y) {
        long t = android.os.SystemClock.uptimeMillis();
        if (t - lastMoveSent < 4) return;   // не частіше ~250 Гц
        lastMoveSent = t;
        win.pointerMove(x / z(), y / z());
    }

    private boolean mouse(MotionEvent e, int a) {
        float x = e.getX(), y = e.getY();
        if (a == MotionEvent.ACTION_HOVER_EXIT) { win.pointerLeave(); return true; }
        if (a == MotionEvent.ACTION_SCROLL) {
            float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL), h = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
            win.pointerMove(x / z(), y / z());
            if (v != 0) win.pointerAxis(0, -v * 15f, v > 0 ? -1 : 1);
            if (h != 0) win.pointerAxis(1, h * 15f, h > 0 ? 1 : -1);
            return true;
        }
        if (a == MotionEvent.ACTION_DOWN) {
            wm.activate(frame);
            if (wm.dismissPopupsFor(win)) { mode = IGNORE; return true; }
            mode = IDLE;
        }
        if (mode == IGNORE) { if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) mode = IDLE; return true; }
        float lx = x / z(), ly = y / z();
        if (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER) sendMove(x, y);
        else win.pointerMove(lx, ly);
        int now = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL ? 0 : e.getButtonState();
        if (a == MotionEvent.ACTION_DOWN && now == 0) now = MotionEvent.BUTTON_PRIMARY;
        diffButtons(now, prevButtons, lx, ly);
        prevButtons = now;
        return true;
    }

    private void diffButtons(int now, int prev, float lx, float ly) {
        int[][] map = {{MotionEvent.BUTTON_PRIMARY, BTN_LEFT}, {MotionEvent.BUTTON_SECONDARY, BTN_RIGHT}, {MotionEvent.BUTTON_TERTIARY, BTN_MID}};
        for (int[] m : map) {
            boolean n = (now & m[0]) != 0, p = (prev & m[0]) != 0;
            if (n && !p) win.pointerButton(m[1], true, lx, ly);
            else if (!n && p) win.pointerButton(m[1], false, lx, ly);
        }
    }

    private boolean finger(MotionEvent e, int a) {
        float x = e.getX(), y = e.getY();
        switch (a) {
        case MotionEvent.ACTION_DOWN:
            wm.activate(frame);
            if (wm.dismissPopupsFor(win)) { mode = IGNORE; return true; }
            mode = PENDING; downX = x; downY = y; lastX = x; lastY = y;
            win.pointerMove(x / z(), y / z());
            postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            return true;
        case MotionEvent.ACTION_POINTER_DOWN:
            if (mode == IGNORE) return true;
            removeCallbacks(longPress);
            if (mode == DRAG) win.pointerButton(BTN_LEFT, false, x / z(), y / z());
            mode = SCROLL; lastX = e.getX(0); lastY = e.getY(0);
            return true;
        case MotionEvent.ACTION_MOVE:
            if (mode == PENDING) {
                if (Math.hypot(x - downX, y - downY) > slop) {
                    removeCallbacks(longPress);
                    win.pointerButton(BTN_LEFT, true, downX / z(), downY / z());
                    mode = DRAG;
                    sendMove(x, y);
                }
            } else if (mode == DRAG) sendMove(x, y);
            else if (mode == SCROLL) {
                float cx = e.getX(0), cy = e.getY(0), dx = cx - lastX, dy = cy - lastY;
                lastX = cx; lastY = cy;
                if (dy != 0) win.pointerAxis(0, -dy / z(), 0);
                if (dx != 0) win.pointerAxis(1, -dx / z(), 0);
            }
            return true;
        case MotionEvent.ACTION_POINTER_UP:
            return true;
        case MotionEvent.ACTION_UP:
            removeCallbacks(longPress);
            if (mode == PENDING) { win.pointerButton(BTN_LEFT, true, x / z(), y / z()); win.pointerButton(BTN_LEFT, false, x / z(), y / z()); }
            else if (mode == DRAG) { win.pointerMove(x / z(), y / z()); win.pointerButton(BTN_LEFT, false, x / z(), y / z()); }
            mode = IDLE;
            return true;
        case MotionEvent.ACTION_CANCEL:
            removeCallbacks(longPress);
            if (mode == DRAG) win.pointerButton(BTN_LEFT, false, x / z(), y / z());
            mode = IDLE;
            return true;
        }
        return true;
    }

    /** Програма попросила перетягнути/змінити розмір (xdg_toplevel.move/resize) — переходимо в режим EXT, якщо палець ще притиснутий. */
    boolean beginExternal(int kind, int edges) {
        if (mode == IDLE || mode == IGNORE) return false;
        removeCallbacks(longPress);
        if (mode == DRAG) win.pointerButton(BTN_LEFT, false, downX / z(), downY / z());
        mode = EXT;
        frame.extStart(kind, edges, lastRawX, lastRawY);
        return true;
    }

    // ---------------- Клавіатура ----------------

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) { return wm.onKey(frame, e, true) || super.onKeyDown(keyCode, e); }
    @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return wm.onKey(frame, e, false) || super.onKeyUp(keyCode, e); }

    /** Завжди true: вікно має активне з'єднання з IME (кеймапер/«inputtext» вставляє текст через нього). М'яка клавіатура — лише за textEditor. */
    @Override public boolean onCheckIsTextEditor() { return !win.isPopup(); }

    @Override public InputConnection onCreateInputConnection(EditorInfo out) {
        if (win.isPopup()) return null;
        // без кнопки клавіатури: TYPE_NULL — звичайні клавіші йдуть як події, а IME-вставка тексту все одно працює
        out.inputType = !textEditor ? android.text.InputType.TYPE_NULL : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        out.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        return new LinuxIc(this);
    }

    private final class LinuxIc extends BaseInputConnection {
        private String composing = "";
        LinuxIc(View v) { super(v, false); }

        private void apply(String n) {
            int cp = 0, max = Math.min(composing.length(), n.length());
            while (cp < max && composing.charAt(cp) == n.charAt(cp)) cp++;
            for (int i = composing.length() - cp; i > 0; i--) wm.tapKey(frame, 14);
            for (int i = cp; i < n.length(); i++) wm.typeChar(frame, n.charAt(i));
            composing = n;
        }
        @Override public boolean setComposingText(CharSequence t, int p) { apply(t.toString()); return true; }
        @Override public boolean commitText(CharSequence t, int p) { apply(t.toString()); composing = ""; return true; }
        @Override public boolean finishComposingText() { composing = ""; return true; }
        @Override public boolean deleteSurroundingText(int before, int after) {
            if (!composing.isEmpty()) composing = "";
            for (int i = 0; i < before; i++) wm.tapKey(frame, 14);
            for (int i = 0; i < after; i++) wm.tapKey(frame, 111);
            return true;
        }
        @Override public boolean sendKeyEvent(KeyEvent ev) { return wm.onKey(frame, ev, ev.getAction() == KeyEvent.ACTION_DOWN) || super.sendKeyEvent(ev); }
        @Override public boolean performEditorAction(int a) { wm.tapKey(frame, 28); return true; }
        @Override public boolean setSelection(int a, int b) { return true; }
        @Override public CharSequence getTextBeforeCursor(int n, int f) { return ""; }
        @Override public CharSequence getTextAfterCursor(int n, int f) { return ""; }
    }
}
