package com.katelauncher.bjudzetnykukrainianlinuxcommunity.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;
import com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n.I18n;

/**
 * Простий вибір кольору: сітка готових зразків + поле для довільного HEX-коду і жива
 * мініатюра. Кнопка «Скинути» повертає типовий колір KateLauncher.
 */
final class ColorPickerDialog {
    private ColorPickerDialog() {}

    interface OnPicked { void on(int rgb); }

    private static final int[] PRESETS = {
            0xE95420, 0x772953, 0xC34BC3, 0x335DD9, 0x2C9FD9,
            0x19A66E, 0x6DBA3D, 0xE6B800, 0xE88A00, 0xD9534F,
            0x8E44AD, 0x4A4A4A,
    };

    static void show(final Context c, String titleKey, int current, final int def, final OnPicked onPicked) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 16);
        root.setPadding(pad, Ui.dp(c, 8), pad, 0);

        final View preview = new View(c);
        GradientDrawable pd = new GradientDrawable();
        pd.setShape(GradientDrawable.OVAL);
        pd.setColor(0xFF000000 | current);
        preview.setBackground(pd);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48));
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.bottomMargin = Ui.dp(c, 12);

        final EditText hex = new EditText(c);
        hex.setInputType(InputType.TYPE_CLASS_TEXT);
        hex.setText(String.format("#%06X", current & 0xFFFFFF));
        hex.setTextColor(0xFFFFFFFF);
        hex.setHintTextColor(0x80FFFFFF);
        hex.setGravity(Gravity.CENTER);
        Ui.tintEdit(hex);

        final int[] value = {current};
        final Runnable updatePreview = new Runnable() {
            @Override public void run() { pd.setColor(0xFF000000 | value[0]); preview.invalidate(); }
        };

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(6);
        int sw = Ui.dp(c, 36), m = Ui.dp(c, 4);
        // перший зразок — стандартний відтінок (той самий, що й при першому запуску)
        java.util.List<Integer> swatches = new java.util.ArrayList<>();
        swatches.add(def & 0xFFFFFF);
        for (int pr : PRESETS) if (!swatches.contains(pr)) swatches.add(pr);
        for (final int preset : swatches) {
            View v = new View(c);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0xFF000000 | preset);
            v.setBackground(d);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = sw; lp.height = sw;
            lp.setMargins(m, m, m, m);
            v.setLayoutParams(lp);
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    value[0] = preset;
                    hex.setText(String.format("#%06X", preset));
                    updatePreview.run();
                }
            });
            grid.addView(v);
        }

        hex.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                try {
                    String t = s.toString().trim();
                    if (t.startsWith("#")) t = t.substring(1);
                    if (t.length() == 6) { value[0] = Integer.parseInt(t, 16) & 0xFFFFFF; updatePreview.run(); }
                } catch (NumberFormatException ignored) {}
            }
        });

        root.addView(preview, plp);
        root.addView(grid, centerLp(c));
        TextView hint = Ui.text(c, "#RRGGBB", 12, 0x80FFFFFF);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(c, 14);
        root.addView(hex, hlp);

        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(I18n.t(titleKey))
                .setView(root)
                .setPositiveButton(I18n.t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { onPicked.on(value[0]); }
                })
                .setNeutralButton(I18n.t("color_reset"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { onPicked.on(def); }
                })
                .setNegativeButton(I18n.t("cancel"), null)
                .show();
    }

    private static LinearLayout.LayoutParams centerLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }
}
