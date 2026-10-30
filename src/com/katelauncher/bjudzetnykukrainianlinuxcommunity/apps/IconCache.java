package com.katelauncher.bjudzetnykukrainianlinuxcommunity.apps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Shapes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IconCache {
    private IconCache() {}

    public interface Callback { void onIcon(Bitmap b); }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, List<Callback>> INFLIGHT = new HashMap<>();

    private static String key(AppEntry e, int px, String shape) { return e.key + "@" + px + "@" + shape; }

    public static Bitmap peek(AppEntry e, int px, String shape) { return CACHE.get(key(e, px, shape)); }

    public static void clear() { CACHE.evictAll(); }

    public static void load(Context ctx, final AppEntry e, final int px, final String shape, Callback cb) {
        final String k = key(e, px, shape);
        List<Callback> waiting = INFLIGHT.get(k);
        if (waiting != null) { waiting.add(cb); return; }
        waiting = new ArrayList<>();
        waiting.add(cb);
        INFLIGHT.put(k, waiting);
        final Context app = ctx.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override public void run() {
                final Bitmap b = render(app, e, px, shape);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        List<Callback> cbs = INFLIGHT.remove(k);
                        if (b == null) return;
                        CACHE.put(k, b);
                        if (cbs != null) for (Callback c : cbs) c.onIcon(b);
                    }
                });
            }
        });
    }

    private static Bitmap render(Context app, AppEntry e, int px, String shape) {
        try {
            Drawable d = app.getPackageManager().getActivityIcon(e.cn);
            Bitmap raw = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas rc = new Canvas(raw);
            d.setBounds(0, 0, px, px);
            d.draw(rc);
            if ("square".equals(shape)) return raw;
            Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas oc = new Canvas(out);
            Path p = Shapes.path(shape, px);
            oc.save();
            oc.clipPath(p);
            oc.drawBitmap(raw, 0, 0, null);
            oc.restore();
            return out;
        } catch (Exception ex) {
            return null;
        }
    }
}
