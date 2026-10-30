package com.gnome.launcher.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Size;
import android.view.View;

import com.gnome.launcher.core.Theme;
import com.gnome.launcher.core.Ui;
import com.gnome.launcher.wallpaper.WallpaperStore;

import java.io.File;

/** Мініатюра поточних шпалер (з урахуванням обрізки) або однотонного фону. */
final class WallpaperPreview extends View {
    private static final int MAX_SIDE = 1024;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF(), dst = new RectF();
    private final float radius;
    private final RectF crop;
    private Bitmap bmp;
    private boolean detached;

    WallpaperPreview(Context c) { this(c, false); }

    WallpaperPreview(Context c, boolean land) {
        super(c);
        crop = WallpaperStore.crop(land);
        radius = Ui.dp(c, 12);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(c, 1));
        edge.setColor(0x33FFFFFF);
        load(c.getApplicationContext(), land);
    }

    private void load(final Context c, boolean land) {
        if (!WallpaperStore.has(c, land)) return;
        final File f = WallpaperStore.current(c, land);
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try {
                    b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), new ImageDecoder.OnHeaderDecodedListener() {
                        @Override public void onHeaderDecoded(ImageDecoder d, ImageDecoder.ImageInfo info, ImageDecoder.Source s) {
                            Size sz = info.getSize();
                            int sample = 1;
                            while (Math.max(sz.getWidth(), sz.getHeight()) / sample > MAX_SIDE) sample *= 2;
                            if (sample > 1) d.setTargetSampleSize(sample);
                            d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        }
                    });
                } catch (Exception ignored) {}
                final Bitmap res = b;
                post(new Runnable() {
                    @Override public void run() { if (!detached) { bmp = res; invalidate(); } }
                });
            }
        }, "wp-preview").start();
    }

    @Override protected void onDetachedFromWindow() { detached = true; super.onDetachedFromWindow(); }

    @Override
    protected void onDraw(Canvas c) {
        float vw = getWidth(), vh = getHeight();
        float inset = edge.getStrokeWidth() / 2f;
        box.set(inset, inset, vw - inset, vh - inset);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (bmp == null) {
            c.drawColor(Theme.DESKTOP_BG);
        } else {
            // та сама математика «cover + обрізка», що й у WallpaperView
            float cw = Math.max(0.01f, crop.right - crop.left);
            float ch = Math.max(0.01f, crop.bottom - crop.top);
            float s = Math.max(vw / (cw * bmp.getWidth()), vh / (ch * bmp.getHeight()));
            float W = bmp.getWidth() * s, H = bmp.getHeight() * s;
            float left = vw / 2f - (crop.left + crop.right) / 2f * W;
            float top = vh / 2f - (crop.top + crop.bottom) / 2f * H;
            dst.set(left, top, left + W, top + H);
            c.drawBitmap(bmp, null, dst, bmpPaint);
        }
        c.restore();
        c.drawPath(clip, edge);
    }
}
