package com.katelauncher.bjudzetnykukrainianlinuxcommunity.desktop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.DragEvent;
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
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.dock.DockStore;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.widget.WidgetStore;

import java.util.ArrayList;
import java.util.List;

public class DesktopView extends View {
    public interface Listener {
        void onEmptyMenu(float rawX, float rawY);
        void onDockChanged();
        void onItemMenu(DesktopItem item, float rawX, float rawY);
    }

    private final List<DesktopItem> items = new ArrayList<>();
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint zonePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final int gap, zoneH, margin, slop;

    private int iconPx;
    private final Runnable invalidator = new Runnable() { @Override public void run() { invalidate(); } };
    private String shape = "round";
    private int cols = 6, rows = 5;
    private float gL, gT, gR, gB; // відступи сітки (залежать від «Зсувати іконки»)
    private float dL, dT, dR, dB; // область, яку зараз займає док
    private float cellW = 1, cellH = 1;
    private Listener listener;

    private DesktopItem pressItem, dragItem;
    private float downX, downY, dragX, dragY, dragStartX, dragStartY, downRawX, downRawY;
    private int hoverCol = -1, hoverRow = -1;
    private boolean overRemove, longFired, moved, externalDragActive;
    private float emptyDownRawX, emptyDownRawY;

    private final FolderPopup.Listener folderListener = new FolderPopup.Listener() {
        @Override public void onLaunch(AppEntry e) { AppLauncher.launch(getContext(), e); }
        @Override public void onChanged() { reload(); }
    };

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (pressItem == null) return;
            longFired = true;
            dragItem = pressItem;
            dragX = downX; dragY = downY;
            dragStartX = downX; dragStartY = downY;
            moved = false;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            updateHover();
            invalidate();
        }
    };

    private final Runnable emptyLongPress = new Runnable() {
        @Override public void run() {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) listener.onEmptyMenu(emptyDownRawX, emptyDownRawY);
        }
    };

    public DesktopView(Context c) {
        super(c);
        gap = Ui.dp(c, 4);
        zoneH = Ui.dp(c, 44);
        margin = Ui.dp(c, 8);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();

        labelPaint.setColor(Theme.TEXT);
        labelPaint.setTextSize(Ui.sp(c, 12));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setShadowLayer(Ui.dp(c, 2), 0, Ui.dp(c, 1), 0xAA000000);

        zonePaint.setColor(Theme.TEXT);
        zonePaint.setTextSize(Ui.sp(c, 14));
        zonePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) { listener = l; }

    public void reload() {
        items.clear();
        items.addAll(DesktopStore.load());
        recalc();
        invalidate();
    }

    public void removeItem(DesktopItem item) {
        items.remove(item);
        DesktopStore.save(items);
        invalidate();
    }

    /** Прибирає значок за ключем (програму, що переїхала в папку). */
    public void removeKey(String key) {
        DesktopItem found = null;
        for (DesktopItem it : items) if (it.key.equals(key)) { found = it; break; }
        if (found == null) return;
        items.remove(found);
        DesktopStore.save(items);
        reflow();
        invalidate();
    }

    /** Підміняє значок програми значком нової папки в тій самій клітинці. */
    public void replaceKey(String oldKey, String newKey) {
        for (int i = 0; i < items.size(); i++) {
            DesktopItem it = items.get(i);
            if (!it.key.equals(oldKey)) continue;
            items.set(i, new DesktopItem(newKey, it.col, it.row));
            DesktopStore.save(items);
            reflow();
            invalidate();
            return;
        }
        DesktopStore.addKey(newKey);
        reload();
    }

    public void setDock(float l, float t, float r, float b, boolean shift) {
        dL = l; dT = t; dR = r; dB = b;
        gL = shift ? l : 0; gT = shift ? t : 0; gR = shift ? r : 0; gB = shift ? b : 0;
        recalc();
        invalidate();
    }

    private boolean overDock(float x, float y) {
        return x < dL || x > getWidth() - dR || y < dT || y > getHeight() - dB;
    }

    private boolean dockVisible() { return dL + dT + dR + dB > 0f; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        recalc();
    }

    private void recalc() {
        iconPx = Ui.dp(getContext(), Prefs.getInt(Prefs.DESKTOP_ICON_SIZE, Config.DESKTOP_ICON_DP));
        shape = Prefs.getString(Prefs.DESKTOP_ICON_SHAPE, "round");
        int cellPx = Ui.dp(getContext(), 72);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || cellPx <= 0) return;
        float aw = w - gL - gR, ah = h - gT - gB;
        cols = Math.max(3, (int) (aw / cellPx));
        rows = Math.max(3, (int) (ah / cellPx));
        cellW = Math.max(1f, aw / cols);
        cellH = Math.max(1f, ah / (float) rows);
        reflow();
    }

    private void reflow() {
        boolean[][] used = new boolean[cols][rows];
        boolean wLand = WidgetStore.isLandscape(getContext());
        for (WidgetStore.Entry we : WidgetStore.load(getContext())) {
            int[] v = we.effective(wLand);
            for (int c = v[0]; c < v[0] + v[2] && c < cols; c++)
                for (int r = v[1]; r < v[1] + v[3] && r < rows; r++)
                    if (c >= 0 && r >= 0) used[c][r] = true;
        }
        // Збережені позиції НЕ змінюються через зміну розміру сітки (док, поворот):
        // зайві іконки лише тимчасово показуються у вільних клітинках.
        boolean changed = false;
        List<DesktopItem> keep = new ArrayList<>();
        for (DesktopItem it : items) {
            if (FolderStore.isFolder(it.key) && FolderStore.find(FolderStore.idOf(it.key)) == null) { changed = true; continue; }
            keep.add(it);
        }
        if (changed) {
            items.clear();
            items.addAll(keep);
            DesktopStore.save(items);
        }
        List<DesktopItem> lost = new ArrayList<>();
        for (DesktopItem it : items) {
            it.dc = -1; it.dr = -1;
            if (it.col >= 0 && it.col < cols && it.row >= 0 && it.row < rows && !used[it.col][it.row]) {
                used[it.col][it.row] = true;
                it.dc = it.col; it.dr = it.row;
            } else lost.add(it);
        }
        for (DesktopItem it : lost) {
            int[] slot = freeSlot(used);
            if (slot != null) { used[slot[0]][slot[1]] = true; it.dc = slot[0]; it.dr = slot[1]; }
        }
    }

    private int[] freeSlot(boolean[][] used) {
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (!used[c][r]) return new int[]{c, r};
        return null;
    }

    private float contentH() { return iconPx + gap + (labelPaint.descent() - labelPaint.ascent()); }
    private float cellLeft(int col) { return gL + col * cellW; }

    @Override
    protected void onDraw(Canvas c) {
        float ch = contentH();
        for (DesktopItem it : items) {
            if (it == dragItem || it.dc < 0) continue;
            drawItem(c, it, cellLeft(it.dc) + cellW / 2f, gT + it.dr * cellH + (cellH - ch) / 2f);
        }
        if (dragItem != null || externalDragActive) {
            float r = Ui.dp(getContext(), 12);
            if (dragItem != null && !overRemove && hoverCol >= 0) {
                DesktopItem o = occupant(hoverCol, hoverRow);
                boolean ok = o == null || o == dragItem;
                int m = Ui.dp(getContext(), 4);
                rect.set(cellLeft(hoverCol) + m, gT + hoverRow * cellH + m, cellLeft(hoverCol) + cellW - m, gT + (hoverRow + 1) * cellH - m);
                fill.setColor(ok ? com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme.accentAlpha(0x44) : 0x33FF0000);
                c.drawRoundRect(rect, r, r, fill);
            }
            rect.set(dL + margin, dT + margin, getWidth() - dR - margin, dT + margin + zoneH);
            fill.setColor(overRemove ? 0xCCD32F2F : 0x66000000);
            c.drawRoundRect(rect, r, r, fill);
            c.drawText(I18n.t("remove_zone"), rect.centerX(), rect.centerY() - (zonePaint.ascent() + zonePaint.descent()) / 2f, zonePaint);
            if (dragItem != null) drawItem(c, dragItem, dragX, dragY - ch / 2f);
        }
    }

    private void drawItem(Canvas c, DesktopItem it, float cx, float top) {
        String label;
        Bitmap b;
        if (FolderStore.isFolder(it.key)) {
            FolderStore.Folder f = FolderStore.find(FolderStore.idOf(it.key));
            if (f == null) return;
            label = f.name;
            b = FolderIcon.render(getContext(), f, iconPx, shape, invalidator);
        } else {
            AppEntry e = AppRepository.find(it.key);
            if (e == null) return;
            label = e.label;
            b = IconCache.peek(e, iconPx, shape);
            if (b == null) {
                IconCache.load(getContext(), e, iconPx, shape, new IconCache.Callback() {
                    @Override public void onIcon(Bitmap bm) { invalidate(); }
                });
            }
        }
        if (b != null) c.drawBitmap(b, cx - iconPx / 2f, top, bmpPaint);
        float maxW = cellW - margin * 2;
        if (it.shown == null || !label.equals(it.shownSrc) || Math.abs(it.shownW - maxW) > 1f) {
            it.shown = TextUtils.ellipsize(label, labelPaint, maxW, TextUtils.TruncateAt.END).toString();
            it.shownSrc = label;
            it.shownW = maxW;
        }
        c.drawText(it.shown, cx, top + iconPx + gap - labelPaint.ascent(), labelPaint);
    }

    private DesktopItem occupant(int col, int row) {
        for (DesktopItem it : items) if (it.dc == col && it.dr == row) return it;
        return null;
    }

    private DesktopItem itemAt(float x, float y) {
        if (overDock(x, y) || x < gL || y < gT) return null;
        return occupant((int) ((x - gL) / cellW), (int) ((y - gT) / cellH));
    }

    private void updateHover() {
        overRemove = !overDock(dragX, dragY) && dragY < dT + margin + zoneH;
        hoverCol = Math.max(0, Math.min(cols - 1, (int) Math.floor((dragX - gL) / cellW)));
        hoverRow = Math.max(0, Math.min(rows - 1, (int) Math.floor((dragY - gT) / cellH)));
    }

    private void drop() {
        if (overRemove) {
            items.remove(dragItem);
            DesktopStore.save(items);
            dragItem = null;
            invalidate();
            return;
        }
        if (dockVisible() && overDock(dragX, dragY)) {
            DockStore.add(dragItem.key);
            items.remove(dragItem);
            DesktopStore.save(items);
            dragItem = null;
            invalidate();
            if (listener != null) listener.onDockChanged();
            return;
        }
        DesktopItem o = occupant(hoverCol, hoverRow);
        if (o == null || o == dragItem) {
            dragItem.col = hoverCol; dragItem.row = hoverRow;
            DesktopStore.save(items);
        } else if (FolderStore.isFolder(o.key) && !FolderStore.isFolder(dragItem.key)) {
            FolderStore.addApp(FolderStore.idOf(o.key), dragItem.key);
            items.remove(dragItem);
            DesktopStore.save(items);
        } else if (!FolderStore.isFolder(o.key) && !FolderStore.isFolder(dragItem.key)) {
            List<String> merged = new ArrayList<>();
            merged.add(o.key); merged.add(dragItem.key);
            String id = FolderStore.createPlaced(I18n.t("folder_default_name"), merged);
            items.remove(o);
            items.remove(dragItem);
            items.add(new DesktopItem(FolderStore.PREFIX + id, o.dc, o.dr));
            DesktopStore.save(items);
        }
        dragItem = null;
        reflow();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x; downY = y; longFired = false;
                downRawX = e.getRawX(); downRawY = e.getRawY();
                pressItem = itemAt(x, y);
                removeCallbacks(emptyLongPress);
                if (pressItem != null) {
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                } else if (!overDock(x, y)) {
                    emptyDownRawX = e.getRawX();
                    emptyDownRawY = e.getRawY();
                    postDelayed(emptyLongPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragItem != null) {
                    dragX = x; dragY = y;
                    if (!moved && (Math.abs(x - dragStartX) > slop || Math.abs(y - dragStartY) > slop)) moved = true;
                    updateHover();
                    invalidate();
                } else if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                    removeCallbacks(longPress);
                    removeCallbacks(emptyLongPress);
                    pressItem = null;
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                removeCallbacks(emptyLongPress);
                if (dragItem != null) {
                    if (moved) {
                        drop();
                    } else {
                        DesktopItem it = dragItem;
                        dragItem = null;
                        invalidate();
                        if (listener != null) listener.onItemMenu(it, downRawX, downRawY);
                    }
                } else if (pressItem != null && !longFired) {
                    if (FolderStore.isFolder(pressItem.key)) {
                        FolderPopup.show(getContext(), FolderStore.idOf(pressItem.key), folderListener);
                    } else {
                        AppEntry app = AppRepository.find(pressItem.key);
                        if (app != null) AppLauncher.launch(getContext(), app);
                    }
                }
                pressItem = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                removeCallbacks(emptyLongPress);
                dragItem = null; pressItem = null;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                externalDragActive = true;
                invalidate();
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                dragX = event.getX(); dragY = event.getY();
                updateHover();
                invalidate();
                return true;
            case DragEvent.ACTION_DROP: {
                dragX = event.getX(); dragY = event.getY();
                updateHover();
                boolean removeHit = overRemove;
                if (event.getClipData() == null || event.getClipData().getItemCount() == 0) return true;
                String key = String.valueOf(event.getClipData().getItemAt(0).getText());
                if (removeHit) {
                    // перетягування з папки на «Прибрати» нічого не робить; з дока — прибирає з дока
                    if (!FolderPopup.isPending(key)) DockStore.remove(key);
                    if (listener != null) listener.onDockChanged();
                } else {
                    DesktopItem o = occupant(hoverCol, hoverRow);
                    boolean placed;
                    if (o == null) {
                        items.add(new DesktopItem(key, hoverCol, hoverRow));
                        DesktopStore.save(items);
                        placed = true;
                    } else {
                        placed = DesktopStore.addKey(key);
                    }
                    if (placed) {
                        if (!FolderPopup.isPending(key)) DockStore.remove(key);
                        FolderPopup.commitDrop(key);
                        reload();
                    }
                    if (listener != null) listener.onDockChanged();
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                externalDragActive = false;
                FolderPopup.clearPending();
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
