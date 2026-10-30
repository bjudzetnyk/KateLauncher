package com.gnome.launcher.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextUtils;
import android.util.LruCache;

import com.gnome.launcher.apps.AppEntry;
import com.gnome.launcher.apps.AppRepository;
import com.gnome.launcher.apps.IconCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Мініатюра папки: напівпрозорий фон у формі іконок + мініатюри програм всередині
 * (сітка 2x2 до 4 програм, 3x3 — до 9). Готові картинки кешуються;
 * якщо якоїсь іконки ще немає в кеші, вона довантажується у фоні, а після цього
 * викликається onUpdate, щоб екран перемалював мініатюру.
 */
public final class FolderIcon {
    private FolderIcon() {}

    private static final int MAX_ITEMS = 9;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /** Скинути кеш мініатюр (разом з IconCache.clear()). */
    public static void clear() { CACHE.evictAll(); }

    public static Bitmap render(final Context c, FolderStore.Folder f, int px, String shape, final Runnable onUpdate) {
        final int size = Math.max(1, px);
        final String shp = shape == null ? "round" : shape;
        final String key = f.id + "|" + size + "|" + shp + "|" + TextUtils.join(",", f.apps);
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        List<AppEntry> entries = new ArrayList<>();
        for (String k : f.apps) {
            if (entries.size() >= MAX_ITEMS) break;
            AppEntry e = AppRepository.find(k);
            if (e != null) entries.add(e);
        }

        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);

        // Фон
        Path bgPath = Shapes.path(shp, size);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0x55FFFFFF);
        cv.drawPath(bgPath, bg);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, size / 48f));
        edge.setColor(0x33FFFFFF);
        cv.drawPath(bgPath, edge);

        // Мініатюри всередині
        int n = entries.size();
        if (n > 0) {
            int grid = n <= 4 ? 2 : 3;
            float pad = size * 0.14f;
            float gap = Math.max(1f, size * 0.05f);
            float cell = (size - pad * 2f - gap * (grid - 1)) / grid;
            final int mini = Math.max(1, (int) cell);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            for (int i = 0; i < n; i++) {
                AppEntry e = entries.get(i);
                float x = pad + (i % grid) * (cell + gap);
                float y = pad + (i / grid) * (cell + gap);
                Bitmap b = IconCache.peek(e, mini, shp);
                if (b != null) {
                    cv.drawBitmap(b, x, y, p);
                } else {
                    IconCache.load(c, e, mini, shp, new IconCache.Callback() {
                        @Override public void onIcon(Bitmap bm) {
                            CACHE.remove(key);
                            if (onUpdate != null) onUpdate.run();
                        }
                    });
                }
            }
        }
        CACHE.put(key, out);
        return out;
    }
}
