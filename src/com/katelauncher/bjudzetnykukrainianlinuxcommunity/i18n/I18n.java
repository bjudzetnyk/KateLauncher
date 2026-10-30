package com.katelauncher.bjudzetnykukrainianlinuxcommunity.i18n;

import android.content.Context;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Prefs;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Поточна локалізація. Англійська завжди завантажується першою як запасна. */
public final class I18n {
    private I18n() {}

    private static volatile Map<String, String> map = new LinkedHashMap<>();
    private static volatile String code = "en";

    public static void init(Context c) {
        load(c, Prefs.getString(Prefs.LANG, ""));
    }

    public static void load(Context c, String wanted) {
        if (wanted == null || wanted.isEmpty()) {
            String sys = Locale.getDefault().getLanguage();
            wanted = LanguageRepository.exists(c, sys) ? sys : "en";
        }
        Map<String, String> m = new LinkedHashMap<>();
        LanguageRepository.readInto(c, "en", m);
        String use = "en";
        if (!"en".equals(wanted) && LanguageRepository.readInto(c, wanted, m)) use = wanted;
        map = m;
        code = use;
    }

    public static String t(String key) {
        String v = map.get(key);
        return v != null ? v : key;
    }

    public static String code() { return code; }

    public static Map<String, String> snapshot() { return new LinkedHashMap<>(map); }
}
