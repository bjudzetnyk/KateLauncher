package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;

/** Іконки Linux-програм: власна з галереї (файл у приватній теці) або стандартна. */
public final class LinuxIcons {
    private LinuxIcons() {}

    private static File dir(Context c) { File d = new File(c.getFilesDir(), "linux_icons"); d.mkdirs(); return d; }
    private static File file(Context c, String id) { return new File(dir(c), id + ".png"); }

    public static Bitmap load(Context c, LinuxApp a, int px) {
        if (a != null && a.customIcon) {
            try {
                Bitmap src = BitmapFactory.decodeFile(file(c, a.id).getAbsolutePath());
                if (src != null) { Bitmap s = Bitmap.createScaledBitmap(src, px, px, true); if (s != src) src.recycle(); return s; }
            } catch (Throwable ignored) {}
        }
        return standard(px);
    }

    /** Стандартна іконка: темний квадрат із «>_» акцентним кольором. */
    public static Bitmap standard(int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF2B2B2B);
        c.drawRoundRect(new RectF(0, 0, px, px), px * 0.2f, px * 0.2f, p);
        p.setColor(Theme.ACCENT);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(px * 0.46f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(">_", px / 2f, px / 2f + px * 0.16f, p);
        return b;
    }

    /** Зберігає вибране зображення (квадрат по центру, до 256 px). */
    public static boolean saveFromUri(Context c, String id, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(uri); BitmapFactory.decodeStream(in, null, o); if (in != null) in.close();
            int ss = 1; while (o.outWidth / (ss * 2) >= 256 && o.outHeight / (ss * 2) >= 256) ss *= 2;
            o = new BitmapFactory.Options(); o.inSampleSize = ss;
            in = c.getContentResolver().openInputStream(uri); Bitmap src = BitmapFactory.decodeStream(in, null, o); if (in != null) in.close();
            if (src == null) return false;
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap out = Bitmap.createScaledBitmap(sq, 256, 256, true);
            FileOutputStream fo = new FileOutputStream(file(c, id));
            out.compress(Bitmap.CompressFormat.PNG, 100, fo); fo.close();
            return true;
        } catch (Throwable t) { return false; }
    }

    public static void delete(Context c, String id) { file(c, id).delete(); }
}
