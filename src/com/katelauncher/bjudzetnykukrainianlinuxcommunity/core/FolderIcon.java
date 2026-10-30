package com.katelauncher.bjudzetnykukrainianlinuxcommunity.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextUtils;
import android.util.LruCache;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppEntry;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.AppRepository;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps.IconCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Мініатюра папки: напівпрозорий фон у формі іконок + мініатюри програм всередині
 * (сітка 2x2 до 4 програм, 3x3 — до 9).
 *
 * У кеш потрапляють лише ПОВНІ мініатюри (усі іконки готові, список програм уже завантажено).
 * Раніше в кеш клалася й неповна картинка (без іконок), яка потім могла лишитися назавжди —
 * звідси «іконки то зникають, то не з'являються». Тепер неповна картинка малюється щоразу
 * заново, а екран перемальовується, щойно приходить наступна іконка.
 */
public final class FolderIcon {
    private FolderIcon() {}

    private static final int MAX_ITEMS = 9;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /** Хто чекає на довантаження іконок цієї мініатюри (за ключем). */
    private static final Map<String, List<Runnable>> WAITING = new HashMap<>();

    /** Скинути кеш мініатюр (разом з IconCache.clear()). */
    public static void clear() { CACHE.evictAll(); }

    public static Bitmap render(final Context c, FolderStore.Folder f, int px, String shape, final Runnable onUpdate) {
        final int size = Math.max(1, px);
        final String shp = shape == null ? "round" : shape;
        final String key = f.id + "|" + size + "|" + shp + "|" + TextUtils.join(",", f.apps);
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        boolean complete = AppRepository.isLoaded();
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
        List<AppEntry> missing = new ArrayList<>();
        final int mini;
        if (n > 0) {
            int grid = n <= 4 ? 2 : 3;
            float pad = size * 0.14f;
            float gap = Math.max(1f, size * 0.05f);
            float cell = (size - pad * 2f - gap * (grid - 1)) / grid;
            mini = Math.max(1, (int) cell);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            for (int i = 0; i < n; i++) {
                AppEntry e = entries.get(i);
                float x = pad + (i % grid) * (cell + gap);
                float y = pad + (i / grid) * (cell + gap);
                Bitmap b = IconCache.peek(e, mini, shp);
                if (b != null) cv.drawBitmap(b, x, y, p);
                else missing.add(e);
            }
        } else {
            mini = 1;
        }

        if (missing.isEmpty() && complete) {
            CACHE.put(key, out);
            return out;
        }

        // Неповна мініатюра: не кешуємо, а просимо перемалювати, коли щось довантажиться.
        if (onUpdate != null) {
            List<Runnable> w = WAITING.get(key);
            if (w == null) { w = new ArrayList<>(); WAITING.put(key, w); }
            if (!w.contains(onUpdate)) w.add(onUpdate);
        }
        for (AppEntry e : missing) {
            IconCache.load(c, e, mini, shp, new IconCache.Callback() {
                @Override public void onIcon(Bitmap bm) { flush(key); }
            });
        }
        if (missing.isEmpty()) {
            // список програм ще не завантажено — перемалюємо, коли він з'явиться
            retryLater(key);
        }
        return out;
    }

    private static void flush(String key) {
        List<Runnable> w = WAITING.remove(key);
        if (w == null) return;
        for (Runnable r : w) r.run();
    }

    private static void retryLater(final String key) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { flush(key); }
        }, 400);
    }
}
