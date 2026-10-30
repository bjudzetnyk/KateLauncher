package com.gnome.launcher.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.gnome.launcher.core.Theme;
import com.gnome.launcher.core.Ui;

/** Легке контекстне меню (PopupWindow). */
public final class MiniMenu {
    private final Context ctx;
    private final LinearLayout box;
    private PopupWindow pw;

    public MiniMenu(Context c) {
        ctx = c;
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(c, Theme.SURFACE, 12));
        int p = Ui.dp(c, 6);
        box.setPadding(0, p, 0, p);
        box.setMinimumWidth(Ui.dp(c, 240));
    }

    public MiniMenu add(String label, final Runnable action) {
        TextView t = Ui.text(ctx, label, 16, Theme.TEXT);
        t.setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 14), Ui.dp(ctx, 20), Ui.dp(ctx, 14));
        t.setBackground(Ui.pressable(ctx, 0x00000000, Theme.SURFACE_HI, 0));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (pw != null) pw.dismiss();
                action.run();
            }
        });
        box.addView(t);
        return this;
    }

    public void showAt(View anchor, int x, int y) {
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int m = Ui.dp(ctx, 8);
        int w = box.getMeasuredWidth();
        int h = box.getMeasuredHeight();
        int px = Math.max(m, Math.min(x, dm.widthPixels - w - m));
        int py = Math.max(m, Math.min(y, dm.heightPixels - h - m));
        pw = new PopupWindow(box, w, h, true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(Ui.dp(ctx, 8));
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, px, py);
    }
}
