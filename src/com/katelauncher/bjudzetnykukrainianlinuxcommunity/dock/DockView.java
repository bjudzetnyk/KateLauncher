package com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppEntry;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppLauncher;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppRepository;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.IconCache;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Config;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.FolderIcon;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.FolderPopup;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.FolderStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DockView extends View {
    public interface Listener {
        void onOpenDrawer();
        void onOpenSettings();
        void onItemMenu(String key, float rawX, float rawY);
        void onOpenFolder(String folderId);
    }

    private static final int HIT_NONE = -1, HIT_APPS = -2, HIT_SETTINGS = -3;

    private final int slot, pad, slop;
    private int iconPx;
    private final Runnable invalidator = new Runnable() { @Override public void run() { invalidate(); } };
    private String shape = "round";
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final List<String> keys = new ArrayList<>();
    private final SwipeTracker tracker = new SwipeTracker();

    private Listener listener;
    private DockController ctl;
    private int side = DockSide.LEFT;
    private boolean horiz, overlay, navEnd = true;
    private float scroll, downM, downRawC, lastM, rawX, rawY;
    private int hit = HIT_NONE, mode;
    private boolean pressed, longFired;

    private final FolderPopup.Listener folderListener = new FolderPopup.Listener() {
        @Override public void onLaunch(AppEntry e) { AppLauncher.launch(getContext(), e); }
        @Override public void onChanged() { reload(); }
    };

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (mode == 0 && hit >= 0 && hit < keys.size()) {
                longFired = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            }
        }
    };

    public DockView(Context c) {
        super(c);
        slot = Ui.dp(c, Config.DOCK_SLOT_DP);
        iconPx = Ui.dp(c, Config.DOCK_ICON_DP);
        pad = Ui.dp(c, 8);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        bg.setColor(Theme.DOCK_BG);
        fill.setColor(0xFFFFFFFF);
        ring.setColor(0xFFFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
    }

    public void setListener(Listener l) { listener = l; }
    public void setController(DockController c) { ctl = c; }
    /** Оверлей поверх програм: без перетягування на стіл і без вікна папки. */
    public void setOverlay(boolean o) { overlay = o; }
    public void setSide(int s, boolean navAtEnd) {
        side = s;
        horiz = DockSide.horizontal(s);
        navEnd = navAtEnd;
        scroll = 0;
        invalidate();
    }

    @Override
    public boolean onDragEvent(android.view.DragEvent event) {
        switch (event.getAction()) {
            case android.view.DragEvent.ACTION_DRAG_STARTED:
                return true;
            case android.view.DragEvent.ACTION_DROP: {
                if (event.getClipData() == null || event.getClipData().getItemCount() == 0) return true;
                String key = String.valueOf(event.getClipData().getItemAt(0).getText());
                if (DockStore.add(key)) { FolderPopup.commitDrop(key); reload(); }
                return true;
            }
            case android.view.DragEvent.ACTION_DRAG_ENDED:
                FolderPopup.clearPending();
                return true;
            default:
                return true;
        }
    }

    public void reload() {
        iconPx = Ui.dp(getContext(), Prefs.getInt(Prefs.DOCK_ICON_SIZE, Config.DOCK_ICON_DP));
        shape = Prefs.getString(Prefs.DOCK_ICON_SHAPE, "round");
        keys.clear();
        for (String k : DockStore.list()) {
            if (FolderStore.isFolder(k)) { if (FolderStore.find(FolderStore.idOf(k)) != null) keys.add(k); }
            else if (AppRepository.find(k) != null) keys.add(k);
        }
        scroll = Math.max(0f, Math.min(scroll, maxScroll()));
        invalidate();
    }

    private Bitmap iconFor(String key, boolean triggerLoad) {
        if (FolderStore.isFolder(key)) {
            FolderStore.Folder f = FolderStore.find(FolderStore.idOf(key));
            return f == null ? null : FolderIcon.render(getContext(), f, iconPx, shape, invalidator);
        }
        AppEntry e = AppRepository.find(key);
        if (e == null) return null;
        Bitmap b = IconCache.peek(e, iconPx, shape);
        if (b == null && triggerLoad) {
            IconCache.load(getContext(), e, iconPx, shape, new IconCache.Callback() {
                @Override public void onIcon(Bitmap bm) { invalidate(); }
            });
        }
        return b;
    }

    private float d(float v) { return v * getResources().getDisplayMetrics().density; }
    // «Головна» вісь — вздовж дока (Y для вертикального, X для горизонтального).
    private float len() { return horiz ? getWidth() : getHeight(); }
    // Кнопки навігації займають по одному слоту: навігація в кінці — [список][меню програм][налаштування],
    // на початку — [налаштування][меню програм][список].
    private float settingsPos() { return navEnd ? len() - pad - slot : pad; }
    private float appsPos() { return navEnd ? len() - pad - 2f * slot : pad + slot; }
    private float listStart() { return navEnd ? pad : pad + 2f * slot; }
    private float listEnd() { return navEnd ? len() - pad - 2f * slot : len() - pad; }
    private float maxScroll() { return Math.max(0f, keys.size() * (float) slot - (listEnd() - listStart())); }
    private float cx(float a) { return horiz ? a : getWidth() / 2f; }
    private float cy(float a) { return horiz ? getHeight() / 2f : a; }
    private float mainOf(MotionEvent e) { return horiz ? e.getX() : e.getY(); }
    private float rawCross(MotionEvent e) { return horiz ? e.getRawY() : e.getRawX(); }

    private int hitTest(float m) {
        if (navEnd) {
            if (m >= settingsPos()) return HIT_SETTINGS;
            if (m >= appsPos()) return HIT_APPS;
            if (m < listStart()) return HIT_NONE;
        } else {
            if (m < settingsPos() + slot) return HIT_SETTINGS;
            if (m < appsPos() + slot) return HIT_APPS;
            if (m >= listEnd()) return HIT_NONE;
        }
        int i = (int) ((m - listStart() + scroll) / slot);
        return i >= 0 && i < keys.size() ? i : HIT_NONE;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = d(16);
        switch (side) {
            case DockSide.RIGHT: c.drawRoundRect(0, 0, w + r, h, r, r, bg); break;
            case DockSide.TOP: c.drawRoundRect(0, -r, w, h, r, r, bg); break;
            case DockSide.BOTTOM: c.drawRoundRect(0, 0, w, h + r, r, r, bg); break;
            default: c.drawRoundRect(-r, 0, w, h, r, r, bg);
        }

        float listTop = listStart(), listBottom = listEnd();
        c.save();
        if (horiz) c.clipRect(listTop, 0, listBottom, h); else c.clipRect(0, listTop, w, listBottom);
        for (int i = 0; i < keys.size(); i++) {
            float a = listTop + i * slot - scroll;
            if (a + slot < listTop || a > listBottom) continue;
            if (pressed && hit == i) highlight(c, a);
            Bitmap b = iconFor(keys.get(i), true);
            if (b != null) {
                float x = horiz ? a + (slot - iconPx) / 2f : (w - iconPx) / 2f;
                float y = horiz ? (h - iconPx) / 2f : a + (slot - iconPx) / 2f;
                c.drawBitmap(b, x, y, bmpPaint);
            }
        }
        c.restore();

        float appsA = appsPos();
        if (pressed && hit == HIT_APPS) highlight(c, appsA);
        drawAppsButton(c, cx(appsA + slot / 2f), cy(appsA + slot / 2f));
        float setA = settingsPos();
        if (pressed && hit == HIT_SETTINGS) highlight(c, setA);
        drawGear(c, cx(setA + slot / 2f), cy(setA + slot / 2f));
    }

    private void highlight(Canvas c, float a) {
        if (horiz) rect.set(a + d(2), d(6), a + slot - d(2), getHeight() - d(6));
        else rect.set(d(6), a + d(2), getWidth() - d(6), a + slot - d(2));
        fill.setColor(0x33FFFFFF);
        c.drawRoundRect(rect, d(12), d(12), fill);
        fill.setColor(0xFFFFFFFF);
    }

    private void drawAppsButton(Canvas c, float cx, float cy) {
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++)
                c.drawCircle(cx + i * d(8), cy + j * d(8), d(2.2f), fill);
    }

    private void drawGear(Canvas c, float cx, float cy) {
        for (int k = 0; k < 8; k++) {
            c.save();
            c.rotate(k * 45f, cx, cy);
            c.drawRect(cx - d(2.5f), cy - d(12f), cx + d(2.5f), cy - d(8f), fill);
            c.restore();
        }
        ring.setStrokeWidth(d(4f));
        c.drawCircle(cx, cy, d(7f), ring);
    }

    private void cancelLong() { removeCallbacks(longPress); }

    private void startItemDrag(String key) {
        Bitmap b = iconFor(key, false);
        ClipData data = ClipData.newPlainText("dock_app", key);
        startDragAndDrop(data, new IconShadow(this, b, iconPx), null, 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float m = mainOf(e);
        int dir = DockSide.hideDir(side);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mode = 0;
                longFired = false;
                downM = m; lastM = m;
                downRawC = rawCross(e);
                rawX = e.getRawX(); rawY = e.getRawY();
                tracker.start(downRawC * -dir, e.getEventTime());
                hit = hitTest(m);
                pressed = hit != HIT_NONE;
                invalidate();
                if (hit >= 0) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;

            case MotionEvent.ACTION_MOVE: {
                rawX = e.getRawX(); rawY = e.getRawY();
                float rc = rawCross(e);
                tracker.move(rc * -dir, e.getEventTime());
                float away = (rc - downRawC) * dir; // >0 — у бік «сховати»
                if (mode == 0) {
                    float dm = m - downM;
                    if (Math.abs(away) > slop && Math.abs(away) > Math.abs(dm)) {
                        if (away < 0 && !overlay && hit >= 0 && hit < keys.size()) {
                            cancelLong();
                            pressed = false;
                            mode = 3;
                            invalidate();
                            startItemDrag(keys.get(hit));
                            return true;
                        }
                        mode = 1;
                        cancelLong();
                        pressed = false;
                        invalidate();
                    } else if (Math.abs(dm) > slop) {
                        if (longFired && hit >= 0 && hit < keys.size()) {
                            mode = 4;
                        } else {
                            mode = 2;
                            cancelLong();
                            pressed = false;
                        }
                        invalidate();
                    }
                }
                if (mode == 3) return true;
                if (mode == 4) {
                    int ti = hitTest(m);
                    if (ti >= 0 && ti < keys.size() && ti != hit) {
                        Collections.swap(keys, hit, ti);
                        hit = ti;
                        invalidate();
                    }
                } else if (mode == 1 && ctl != null) {
                    ctl.dragTo(1f - Math.max(0f, away) / ctl.size());
                } else if (mode == 2) {
                    scroll = Math.max(0f, Math.min(scroll - (m - lastM), maxScroll()));
                    invalidate();
                }
                lastM = m;
                return true;
            }

            case MotionEvent.ACTION_UP:
                cancelLong();
                if (mode == 3) { pressed = false; invalidate(); return true; }
                if (mode == 4) {
                    DockStore.saveOrder(keys);
                    pressed = false;
                    invalidate();
                    return true;
                }
                if (mode == 1 && ctl != null) {
                    ctl.release(tracker.velocity());
                } else if (mode == 0 && longFired && hit >= 0 && hit < keys.size()) {
                    if (listener != null) listener.onItemMenu(keys.get(hit), rawX, rawY);
                } else if (mode == 0 && !longFired) {
                    if (hit == HIT_APPS) { if (listener != null) listener.onOpenDrawer(); }
                    else if (hit == HIT_SETTINGS) { if (listener != null) listener.onOpenSettings(); }
                    else if (hit >= 0 && hit < keys.size()) {
                        String k = keys.get(hit);
                        if (FolderStore.isFolder(k)) {
                            if (overlay) { if (listener != null) listener.onOpenFolder(FolderStore.idOf(k)); }
                            else FolderPopup.show(getContext(), FolderStore.idOf(k), folderListener);
                        } else {
                            AppEntry app = AppRepository.find(k);
                            if (app != null) AppLauncher.launch(getContext(), app);
                        }
                    }
                }
                pressed = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelLong();
                if (mode == 4) reload();
                if (mode == 1 && ctl != null) ctl.release(0);
                pressed = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private static final class IconShadow extends View.DragShadowBuilder {
        private final Bitmap bmp;
        private final int size;
        IconShadow(View v, Bitmap b, int size) { super(v); bmp = b; this.size = size; }
        @Override public void onProvideShadowMetrics(Point outSize, Point outTouch) {
            outSize.set(size, size);
            outTouch.set(size / 2, size / 2);
        }
        @Override public void onDrawShadow(Canvas canvas) {
            if (bmp != null) canvas.drawBitmap(bmp, 0, 0, null);
        }
    }
}
