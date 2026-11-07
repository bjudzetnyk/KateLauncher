package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.Native;

/**
 * Буфер кадру, розбитий на плитки 256x256. Змінена область копіюється лише в зачеплені плитки,
 * тож GPU перезавантажує текстури лише змінених плиток (а не всього кадру).
 */
public final class LinuxBuffer {
    public static final int T = 256;
    public final int w, h;
    private final int tx, ty;
    private final Bitmap[] tiles;

    public LinuxBuffer(int w, int h) {
        this.w = Math.max(1, w); this.h = Math.max(1, h);
        tx = (this.w + T - 1) / T; ty = (this.h + T - 1) / T;
        tiles = new Bitmap[tx * ty];
        for (int j = 0; j < ty; j++)
            for (int i = 0; i < tx; i++)
                tiles[j * tx + i] = Bitmap.createBitmap(Math.min(T, this.w - i * T), Math.min(T, this.h - j * T), Bitmap.Config.ARGB_8888);
    }

    /** Копіює прямокутник із спільної області композитора (RGBA, stride = srcStridePx). Одна memcpy на ряд. */
    public void blitNative(long src, int srcStridePx, int x, int y, int rw, int rh) {
        if (!Native.load()) return;
        int x1 = Math.min(w, x + rw), y1 = Math.min(h, y + rh);
        x = Math.max(0, x); y = Math.max(0, y);
        if (x1 <= x || y1 <= y) return;
        for (int j = y / T; j <= (y1 - 1) / T; j++) {
            for (int i = x / T; i <= (x1 - 1) / T; i++) {
                int ax = Math.max(x, i * T), ay = Math.max(y, j * T);
                int bx = Math.min(x1, (i + 1) * T), by = Math.min(y1, (j + 1) * T);
                Native.nativeBlit(src, srcStridePx, ax, ay, bx - ax, by - ay, tiles[j * tx + i], ax - i * T, ay - j * T);
            }
        }
    }

    /** Те саме, але з Bitmap (для тестового джерела й для середовищ без native-бібліотеки). */
    public void drawFrom(Bitmap src, Rect r) {
        int x = Math.max(0, r.left), y = Math.max(0, r.top), x1 = Math.min(w, r.right), y1 = Math.min(h, r.bottom);
        if (x1 <= x || y1 <= y) return;
        Paint p = new Paint(); p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC));
        for (int j = y / T; j <= (y1 - 1) / T; j++) {
            for (int i = x / T; i <= (x1 - 1) / T; i++) {
                Canvas c = new Canvas(tiles[j * tx + i]);
                c.translate(-i * T, -j * T);
                c.clipRect(Math.max(x, i * T), Math.max(y, j * T), Math.min(x1, (i + 1) * T), Math.min(y1, (j + 1) * T));
                c.drawBitmap(src, 0, 0, p);
            }
        }
    }

    /** Малює лише плитки, що перетинають поточний clip канви (координати — у пікселях буфера). */
    public void draw(Canvas c, Paint p) {
        Rect clip = c.getClipBounds();
        int i0 = Math.max(0, clip.left / T), i1 = Math.min(tx - 1, (clip.right - 1) / T);
        int j0 = Math.max(0, clip.top / T), j1 = Math.min(ty - 1, (clip.bottom - 1) / T);
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++)
                c.drawBitmap(tiles[j * tx + i], i * T, j * T, p);
    }

    public void recycle() {
        for (int i = 0; i < tiles.length; i++) if (tiles[i] != null) { tiles[i].recycle(); tiles[i] = null; }
    }
}
