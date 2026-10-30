package com.gnome.launcher.i18n;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Мови: вбудовані лежать в assets/lang/<код>.json, користувацькі — у files/lang/<код>.json.
 * Користувацький файл з тим самим кодом перекриває вбудований.
 */
public final class LanguageRepository {
    private LanguageRepository() {}

    public static final class Lang {
        public final String code, name;
        public final boolean user;
        Lang(String code, String name, boolean user) { this.code = code; this.name = name; this.user = user; }
    }

    public static File userDir(Context c) {
        File d = new File(c.getFilesDir(), "lang");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String readAll(InputStream in) throws IOException {
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static String readRaw(Context c, String code) throws IOException {
        File f = new File(userDir(c), code + ".json");
        InputStream in = f.exists() ? new FileInputStream(f) : c.getAssets().open("lang/" + code + ".json");
        return readAll(in);
    }

    public static boolean exists(Context c, String code) {
        if (new File(userDir(c), code + ".json").exists()) return true;
        try {
            c.getAssets().open("lang/" + code + ".json").close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean readInto(Context c, String code, Map<String, String> out) {
        try {
            JSONObject o = new JSONObject(readRaw(c, code));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.startsWith("_")) continue;
                out.put(k, o.optString(k));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String nameOf(Context c, String code) {
        try {
            return new JSONObject(readRaw(c, code)).optString("_name", code);
        } catch (Exception e) {
            return code;
        }
    }

    public static List<Lang> list(Context c) {
        Map<String, Boolean> codes = new TreeMap<>(); // код -> чи користувацький
        try {
            String[] a = c.getAssets().list("lang");
            if (a != null) for (String n : a) if (n.endsWith(".json")) codes.put(n.substring(0, n.length() - 5), false);
        } catch (IOException ignored) {}
        File[] fs = userDir(c).listFiles();
        if (fs != null) for (File f : fs) {
            String n = f.getName();
            if (n.endsWith(".json")) codes.put(n.substring(0, n.length() - 5), true);
        }
        List<Lang> out = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : codes.entrySet()) {
            out.add(new Lang(e.getKey(), nameOf(c, e.getKey()), e.getValue()));
        }
        return out;
    }

    /** Імпорт файлу локалізації. Код мови береться з імені файлу. Повертає код. */
    public static String importFrom(Context c, Uri uri) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("no stream");
        String text = readAll(in);
        JSONObject o = new JSONObject(text); // перевірка, що це JSON
        String code = toCode(displayName(c, uri));
        if (!o.has("_name")) {
            o.put("_name", code);
            text = o.toString(2);
        }
        try (OutputStream out = new FileOutputStream(new File(userDir(c), code + ".json"))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return code;
    }

    /** Зберігає у вибрану папку повну копію поточної мови (з англійськими запасними ключами). */
    public static void exportCurrent(Context c, Uri tree) throws Exception {
        String code = I18n.code();
        JSONObject o = new JSONObject();
        o.put("_name", nameOf(c, code));
        for (Map.Entry<String, String> e : I18n.snapshot().entrySet()) o.put(e.getKey(), e.getValue());

        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri doc = DocumentsContract.createDocument(c.getContentResolver(), parent, "application/json", code + ".json");
        if (doc == null) throw new IOException("createDocument failed");
        try (OutputStream out = c.getContentResolver().openOutputStream(doc)) {
            if (out == null) throw new IOException("no stream");
            out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Видаляє імпортовану (користувацьку) мову. Вбудовані мови так прибрати не можна. */
    public static boolean forget(Context c, String code) {
        File f = new File(userDir(c), code + ".json");
        return f.exists() && f.delete();
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {}
        return "custom";
    }

    private static String toCode(String name) {
        if (name == null) name = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (s.replace("_", "").isEmpty()) s = "custom";
        return s;
    }
}
