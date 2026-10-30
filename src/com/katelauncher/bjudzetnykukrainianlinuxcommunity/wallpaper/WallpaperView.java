package com.katelauncher.bjudzetnykukrainianlinuxcommunity.wallpaper;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.util.Size;
import android.view.View;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;

import java.io.File;

public class WallpaperView extends View {
    private static final int MAX_SIDE = 2048;

    private Drawable d;
    private int srcW, srcH, loadedVer = -1;
    private boolean loadedLand;
    private float cl, ct, cr = 1f, cb = 1f;

    public WallpaperView(Context c) { super(c); }

    private boolean land() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /** Чи треба перезавантажити: змінилась версія шпалер АБО (при увімкнених окремих
     *  шпалерах) помінялась орієнтація, тож слот змінився. Жодна зайва шпалера не
     *  тримається у пам'яті — лише та, що відповідає поточному стану. */
    public boolean needsReload() {
        if (loadedVer != Prefs.getInt(Prefs.WP_VER, 0)) return true;
        boolean wantLand = WallpaperStore.split() && land();
        return wantLand != loadedLand;
    }

    public void reload() {
        release();
        loadedVer = Prefs.getInt(Prefs.WP_VER, 0);
        boolean useLand = WallpaperStore.split() && land();
        loadedLand = useLand;
        File f = WallpaperStore.current(getContext(), useLand);
        if (WallpaperStore.has(getContext(), useLand)) {
            try {
                d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f), new ImageDecoder.OnHeaderDecodedListener() {
                    @Override
                    public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info, ImageDecoder.Source src) {
                        Size sz = info.getSize();
                        srcW = sz.getWidth();
                        srcH = sz.getHeight();
                        int sample = 1;
                        while (Math.max(srcW, srcH) / sample > MAX_SIDE) sample *= 2;
                        if (sample > 1) decoder.setTargetSampleSize(sample);
                    }
                });
                RectF c = WallpaperStore.crop(useLand);
                cl = c.left; ct = c.top; cr = c.right; cb = c.bottom;
                d.setCallback(this);
                if (d instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) d).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                }
            } catch (Exception e) {
                d = null;
            }
        }
        updateBounds();
        sync();
        invalidate();
    }

    private void release() {
        if (d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).stop();
        if (d != null) d.setCallback(null);
        d = null;
    }

    private void updateBounds() {
        if (d == null || getWidth() == 0 || srcW <= 0 || srcH <= 0) return;
        float vw = getWidth(), vh = getHeight();
        float cw = Math.max(0.01f, (cr - cl)) * srcW;
        float ch = Math.max(0.01f, (cb - ct)) * srcH;
        float s = Math.max(vw / cw, vh / ch);
        float W = srcW * s, H = srcH * s;
        float left = vw / 2f - (cl + cr) / 2f * W;
        float top = vh / 2f - (ct + cb) / 2f * H;
        d.setBounds(Math.round(left), Math.round(top), Math.round(left + W), Math.round(top + H));
    }

    private void sync() {
        if (d instanceof AnimatedImageDrawable) {
            AnimatedImageDrawable a = (AnimatedImageDrawable) d;
            if (isShown() && getWindowVisibility() == VISIBLE) a.start(); else a.stop();
        }
    }

    /** Викликається, коли лаунчер іде у фон (відкрито іншу програму на весь екран). */
    public void pause() {
        if (d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).stop();
    }

    /** Викликається при поверненні на робочий стіл. */
    public void resume() { sync(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (needsReload()) reload(); else updateBounds();
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        sync();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sync();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == d || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (d == null) c.drawColor(Theme.DESKTOP_BG);
        else d.draw(c);
    }
}
