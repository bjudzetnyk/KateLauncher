package com.gnome.launcher.core;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
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
import com.gnome.launcher.apps.AppRepository;
import com.gnome.launcher.apps.IconCache;
import com.gnome.launcher.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Вікно вмісту папки, додане прямо в дерево вікна активності (не окреме Window),
 * щоб системне перетягування (startDragAndDrop) надійно долітало до робочого стола й дока.
 * Запуск програм, витягування на стіл/у док перетягуванням, перейменування, видалення.
 */
public final class FolderPopup {
    private FolderPopup() {}

    // програма, яку зараз тягнуть з папки на стіл/у док (видаляється з папки лише після вдалого скидання)
    private static String pendFolder, pendKey;

    public static boolean isPending(String key) { return pendKey != null && pendKey.equals(key); }
    public static void clearPending() { pendFolder = null; pendKey = null; }
    public static void commitDrop(String key) {
        if (isPending(key)) FolderStore.removeApp(pendFolder, key);
        clearPending();
    }

    public interface Listener {
        void onLaunch(AppEntry e);
        void onChanged();
    }

    public static void show(final Context c, final String folderId, final Listener listener) {
        final FolderStore.Folder folder = FolderStore.find(folderId);
        if (folder == null) return;
        final ViewGroup contentRoot = (ViewGroup) ((Activity) c).findViewById(android.R.id.content);
        if (contentRoot == null) return;

        final List<AppEntry> apps = new ArrayList<>();
        for (String k : folder.apps) { AppEntry e = AppRepository.find(k); if (e != null) apps.add(e); }

        final FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(0xAA000000);

        final LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(c, Theme.SURFACE, 18));
        int cp = Ui.dp(c, 12);
        card.setPadding(cp, cp, cp, cp);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                Ui.dp(c, 320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);

        TextView title = Ui.text(c, folder.name, 17, Theme.TEXT);
        title.setPadding(0, 0, 0, Ui.dp(c, 8));
        card.addView(title);

        final GridView grid = new GridView(c);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(Ui.dp(c, 8));
        final int[] dragIdx = {-1};
        final DragWrap wrap = new DragWrap(c);
        wrap.grid = grid;
        wrap.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(wrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 280)));

        LinearLayout btnRow = new LinearLayout(c);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(btnRow);

        scrim.addView(card, cardLp);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { contentRoot.removeView(scrim); }
        });
        card.setClickable(true); // не закривати по тапу на самій картці
        final Runnable dismiss = new Runnable() {
            @Override public void run() { contentRoot.removeView(scrim); }
        };

        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int i) { return apps.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                LinearLayout cell = convert instanceof LinearLayout ? (LinearLayout) convert : cell(c);
                final AppEntry e = apps.get(i);
                final ImageView icon = (ImageView) cell.getChildAt(0);
                TextView label = (TextView) cell.getChildAt(1);
                label.setText(e.label);
                final int px = Ui.dp(c, 48);
                Bitmap b = IconCache.peek(e, px, "round");
                if (b != null) icon.setImageBitmap(b);
                else {
                    icon.setImageDrawable(null);
                    IconCache.load(c, e, px, "round", new IconCache.Callback() {
                        @Override public void onIcon(Bitmap bm) { grid.invalidateViews(); }
                    });
                }
                cell.setAlpha(i == dragIdx[0] ? 0.35f : 1f);
                return cell;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= apps.size()) return;
                AppEntry e = apps.get(pos);
                dismiss.run();
                if (listener != null) listener.onLaunch(e);
            }
        });
        final Runnable saveOrder = new Runnable() {
            @Override public void run() {
                List<String> ks = new ArrayList<>();
                for (AppEntry e : apps) ks.add(e.key);
                FolderStore.reorder(folderId, ks);
            }
        };
        final int px = Ui.dp(c, 48);
        wrap.cb = new DragWrap.Cb() {
            @Override public void onLong(int pos) { dragIdx[0] = pos; grid.invalidateViews(); }
            @Override public void onMove(float rx, float ry) {
                if (dragIdx[0] < 0) return;
                int[] cl = new int[2];
                card.getLocationOnScreen(cl);
                if (rx < cl[0] || ry < cl[1] || rx > cl[0] + card.getWidth() || ry > cl[1] + card.getHeight()) {
                    // палець вийшов за межі вікна папки — далі це перетягування на стіл/у док
                    AppEntry e = apps.get(dragIdx[0]);
                    saveOrder.run();
                    dragIdx[0] = -1;
                    pendFolder = folderId;
                    pendKey = e.key;
                    wrap.startDragAndDrop(ClipData.newPlainText("folder_app", e.key),
                            new IconShadow(wrap, IconCache.peek(e, px, "round"), px), null, 0);
                    dismiss.run();
                    return;
                }
                int[] gl = new int[2];
                grid.getLocationOnScreen(gl);
                int pos = grid.pointToPosition(Math.round(rx - gl[0]), Math.round(ry - gl[1]));
                if (pos >= 0 && pos < apps.size() && pos != dragIdx[0]) {
                    apps.add(pos, apps.remove(dragIdx[0]));
                    dragIdx[0] = pos;
                    grid.invalidateViews();
                }
            }
            @Override public void onEnd(boolean cancel) {
                if (dragIdx[0] < 0) return;
                dragIdx[0] = -1;
                saveOrder.run();
                grid.invalidateViews();
                if (listener != null) listener.onChanged();
            }
        };

        addBtn(c, btnRow, I18n.t("folder_rename"), new Runnable() {
            @Override public void run() { renameDialog(c, folderId, listener); }
        });
        addBtn(c, btnRow, I18n.t("folder_delete"), new Runnable() {
            @Override public void run() {
                FolderStore.delete(folderId);
                dismiss.run();
                if (listener != null) listener.onChanged();
            }
        });
        addBtn(c, btnRow, I18n.t("cancel"), new Runnable() { @Override public void run() { dismiss.run(); } });

        contentRoot.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static void addBtn(Context c, LinearLayout row, String label, final Runnable action) {
        TextView t = Ui.text(c, label, 14, Theme.ACCENT);
        int p = Ui.dp(c, 10);
        t.setPadding(p, p, p, p);
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { action.run(); } });
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private static void renameDialog(final Context c, final String id, final Listener listener) {
        final EditText input = new EditText(c);
        FolderStore.Folder f = FolderStore.find(id);
        if (f != null) input.setText(f.name);
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t("folder_rename"))
                .setView(input)
                .setPositiveButton(I18n.t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) FolderStore.rename(id, n);
                        if (listener != null) listener.onChanged();
                    }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }

    private static LinearLayout cell(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(c, 4);
        l.setPadding(p, p, p, p);
        ImageView icon = new ImageView(c);
        l.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        TextView label = Ui.text(c, "", 11, Theme.TEXT);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        l.addView(label, new LinearLayout.LayoutParams(Ui.dp(c, 72), ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** Обгортка сітки: довге натискання вмикає перетягування (зміна порядку або винесення з папки). */
    private static final class DragWrap extends FrameLayout {
        interface Cb { void onLong(int pos); void onMove(float rx, float ry); void onEnd(boolean cancel); }
        GridView grid;
        Cb cb;
        private boolean active;
        private int pressPos = -1;
        private float dx, dy;
        private long downTime;
        private final int slop;
        private final Runnable lp = new Runnable() {
            @Override public void run() {
                if (pressPos < 0 || cb == null) return;
                active = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                MotionEvent cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, dx, dy, 0);
                DragWrap.super.dispatchTouchEvent(cancel);
                cancel.recycle();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                cb.onLong(pressPos);
            }
        };

        DragWrap(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime = e.getDownTime();
                    dx = e.getX(); dy = e.getY();
                    active = false;
                    pressPos = grid.pointToPosition((int) dx, (int) dy);
                    postDelayed(lp, ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!active && Math.hypot(e.getX() - dx, e.getY() - dy) > slop) removeCallbacks(lp);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lp);
                    if (active) {
                        active = false;
                        if (cb != null) cb.onEnd(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                        return true;
                    }
                    break;
            }
            if (active) {
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE && cb != null) cb.onMove(e.getRawX(), e.getRawY());
                return true;
            }
            return super.dispatchTouchEvent(e);
        }
    }

    private static final class IconShadow extends View.DragShadowBuilder {
        private final Bitmap bmp;
        private final int size;
        IconShadow(View v, Bitmap b, int size) { super(v); bmp = b; this.size = size; }
        @Override public void onProvideShadowMetrics(Point outSize, Point outTouch) {
            outSize.set(size, size);
            outTouch.set(size / 2, size / 2);
        }
        @Override public void onDrawShadow(android.graphics.Canvas canvas) {
            if (bmp != null) canvas.drawBitmap(bmp, 0, 0, null);
        }
    }
}
