package com.katelauncher.bjudzetnykukrainianlinuxcommunity.wallpaper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.SystemUi;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Theme;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Екран обрізки шпалер: рух, масштаб, поворот (поворот лише для не-GIF). */
public class WallpaperCropActivity extends Activity {
    public static final String EXTRA_LAND = "land";

    private CropView crop;
    private int quarter; // кількість поворотів на 90°
    private boolean land;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemUi.apply(this);
        land = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LAND, false);

        File f = WallpaperStore.pending(this);
        Bitmap bmp = null;
        try {
            bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), new ImageDecoder.OnHeaderDecodedListener() {
                @Override
                public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info, ImageDecoder.Source src) {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    Size sz = info.getSize();
                    int sample = 1;
                    while (Math.max(sz.getWidth(), sz.getHeight()) / sample > 2048) sample *= 2;
                    if (sample > 1) decoder.setTargetSampleSize(sample);
                }
            });
        } catch (Exception ignored) {}

        if (bmp == null) {
            Toast.makeText(this, I18n.t("wallpaper_failed"), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        boolean gif = isGif(f);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        crop = new CropView(this);
        crop.setFrameAspect((float) dm.widthPixels / dm.heightPixels);
        crop.setBitmap(bmp);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(0xFF101010);
        int p = Ui.dp(this, 8);
        bar.setPadding(p, p, p, p);

        TextView cancel = button(I18n.t("crop_cancel"), 0x00000000);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                WallpaperStore.pending(WallpaperCropActivity.this).delete();
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        bar.addView(cancel, lp());

        if (!gif) {
            TextView rotate = button(I18n.t("crop_rotate"), 0x00000000);
            rotate.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    quarter = (quarter + 1) % 4;
                    crop.rotate90();
                }
            });
            bar.addView(rotate, lp());
        }

        TextView apply = button(I18n.t("crop_apply"), Theme.ACCENT);
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { apply(); }
        });
        bar.addView(apply, lp());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.addView(crop, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void apply() {
        RectF fr = crop.getCropFractions();
        try {
            if (quarter != 0) WallpaperStore.applyBaked(this, crop.getBitmap(), fr, land);
            else WallpaperStore.apply(this, fr, land);
            setResult(RESULT_OK);
        } catch (IOException e) {
            Toast.makeText(this, I18n.t("wallpaper_failed"), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private TextView button(String label, int bg) {
        TextView t = Ui.text(this, label, 15, Theme.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        t.setBackground(Ui.pressable(this, bg, Theme.SURFACE_HI, 8));
        return t;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = Ui.dp(this, 4);
        l.setMargins(m, 0, m, 0);
        return l;
    }

    private static boolean isGif(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] h = new byte[3];
            return in.read(h) == 3 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F';
        } catch (IOException e) {
            return false;
        }
    }
}
