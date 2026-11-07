package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl;

import java.util.HashMap;
import java.util.Map;

/**
 * Таблиця клавіш двох розкладок (група 0 — English US, група 1 — Українська) і генератор xkb_keymap.
 * Чиста Java (без Android): використовується і в процесі композитора (генерація keymap),
 * і в лаунчері (символ -> клавіша для екранної клавіатури). Самодостатній keymap (без include),
 * тож клієнтам у дистрибутиві не потрібні xkb-data.
 */
public final class KeyTable {
    private KeyTable() {}

    /** Клавіша, що дає символ. */
    public static final class Hit {
        public final int code, group; public final boolean shift;
        Hit(int code, int group, boolean shift) { this.code = code; this.group = group; this.shift = shift; }
    }

    // code, xkb-ім'я, тип(гр.0), тип(гр.1), us0, us1, ua0, ua1   (T — TWO_LEVEL, A — ALPHABETIC)
    private static final String[][] CHARS = {
        {"41", "TLDE", "T", "T", "`", "~", "'", "~"},
        {"2", "AE01", "T", "T", "1", "!", "1", "!"},
        {"3", "AE02", "T", "T", "2", "@", "2", "\""},
        {"4", "AE03", "T", "T", "3", "#", "3", "\u2116"},
        {"5", "AE04", "T", "T", "4", "$", "4", ";"},
        {"6", "AE05", "T", "T", "5", "%", "5", "%"},
        {"7", "AE06", "T", "T", "6", "^", "6", ":"},
        {"8", "AE07", "T", "T", "7", "&", "7", "?"},
        {"9", "AE08", "T", "T", "8", "*", "8", "*"},
        {"10", "AE09", "T", "T", "9", "(", "9", "("},
        {"11", "AE10", "T", "T", "0", ")", "0", ")"},
        {"12", "AE11", "T", "T", "-", "_", "-", "_"},
        {"13", "AE12", "T", "T", "=", "+", "=", "+"},
        {"16", "AD01", "A", "A", "q", "Q", "\u0439", "\u0419"},
        {"17", "AD02", "A", "A", "w", "W", "\u0446", "\u0426"},
        {"18", "AD03", "A", "A", "e", "E", "\u0443", "\u0423"},
        {"19", "AD04", "A", "A", "r", "R", "\u043A", "\u041A"},
        {"20", "AD05", "A", "A", "t", "T", "\u0435", "\u0415"},
        {"21", "AD06", "A", "A", "y", "Y", "\u043D", "\u041D"},
        {"22", "AD07", "A", "A", "u", "U", "\u0433", "\u0413"},
        {"23", "AD08", "A", "A", "i", "I", "\u0448", "\u0428"},
        {"24", "AD09", "A", "A", "o", "O", "\u0449", "\u0429"},
        {"25", "AD10", "A", "A", "p", "P", "\u0437", "\u0417"},
        {"26", "AD11", "T", "A", "[", "{", "\u0445", "\u0425"},
        {"27", "AD12", "T", "A", "]", "}", "\u0457", "\u0407"},
        {"30", "AC01", "A", "A", "a", "A", "\u0444", "\u0424"},
        {"31", "AC02", "A", "A", "s", "S", "\u0456", "\u0406"},
        {"32", "AC03", "A", "A", "d", "D", "\u0432", "\u0412"},
        {"33", "AC04", "A", "A", "f", "F", "\u0430", "\u0410"},
        {"34", "AC05", "A", "A", "g", "G", "\u043F", "\u041F"},
        {"35", "AC06", "A", "A", "h", "H", "\u0440", "\u0420"},
        {"36", "AC07", "A", "A", "j", "J", "\u043E", "\u041E"},
        {"37", "AC08", "A", "A", "k", "K", "\u043B", "\u041B"},
        {"38", "AC09", "A", "A", "l", "L", "\u0434", "\u0414"},
        {"39", "AC10", "T", "A", ";", ":", "\u0436", "\u0416"},
        {"40", "AC11", "T", "A", "'", "\"", "\u0454", "\u0404"},
        {"43", "BKSL", "T", "A", "\\", "|", "\u0491", "\u0490"},
        {"44", "AB01", "A", "A", "z", "Z", "\u044F", "\u042F"},
        {"45", "AB02", "A", "A", "x", "X", "\u0447", "\u0427"},
        {"46", "AB03", "A", "A", "c", "C", "\u0441", "\u0421"},
        {"47", "AB04", "A", "A", "v", "V", "\u043C", "\u041C"},
        {"48", "AB05", "A", "A", "b", "B", "\u0438", "\u0418"},
        {"49", "AB06", "A", "A", "n", "N", "\u0442", "\u0422"},
        {"50", "AB07", "A", "A", "m", "M", "\u044C", "\u042C"},
        {"51", "AB08", "T", "A", ",", "<", "\u0431", "\u0411"},
        {"52", "AB09", "T", "A", ".", ">", "\u044E", "\u042E"},
        {"53", "AB10", "T", "T", "/", "?", ".", ","},
    };

    // code, ім'я, keysym (однаковий в обох групах)
    private static final String[][] FUNC = {
        {"1", "ESC", "Escape"}, {"14", "BKSP", "BackSpace"}, {"28", "RTRN", "Return"},
        {"29", "LCTL", "Control_L"}, {"42", "LFSH", "Shift_L"}, {"54", "RTSH", "Shift_R"},
        {"56", "LALT", "Alt_L"}, {"57", "SPCE", "space"}, {"58", "CAPS", "Caps_Lock"},
        {"59", "FK01", "F1"}, {"60", "FK02", "F2"}, {"61", "FK03", "F3"}, {"62", "FK04", "F4"},
        {"63", "FK05", "F5"}, {"64", "FK06", "F6"}, {"65", "FK07", "F7"}, {"66", "FK08", "F8"},
        {"67", "FK09", "F9"}, {"68", "FK10", "F10"}, {"87", "FK11", "F11"}, {"88", "FK12", "F12"},
        {"69", "NMLK", "Num_Lock"}, {"70", "SCLK", "Scroll_Lock"},
        {"71", "KP7", "KP_7"}, {"72", "KP8", "KP_8"}, {"73", "KP9", "KP_9"}, {"74", "KPSU", "KP_Subtract"},
        {"75", "KP4", "KP_4"}, {"76", "KP5", "KP_5"}, {"77", "KP6", "KP_6"}, {"78", "KPAD", "KP_Add"},
        {"79", "KP1", "KP_1"}, {"80", "KP2", "KP_2"}, {"81", "KP3", "KP_3"}, {"82", "KP0", "KP_0"},
        {"83", "KPDL", "KP_Decimal"}, {"55", "KPMU", "KP_Multiply"}, {"96", "KPEN", "KP_Enter"}, {"98", "KPDV", "KP_Divide"},
        {"97", "RCTL", "Control_R"}, {"99", "PRSC", "Print"}, {"100", "RALT", "Alt_R"},
        {"102", "HOME", "Home"}, {"103", "UP", "Up"}, {"104", "PGUP", "Prior"}, {"105", "LEFT", "Left"},
        {"106", "RGHT", "Right"}, {"107", "END", "End"}, {"108", "DOWN", "Down"}, {"109", "PGDN", "Next"},
        {"110", "INS", "Insert"}, {"111", "DELE", "Delete"}, {"125", "LWIN", "Super_L"}, {"126", "RWIN", "Super_R"},
        {"127", "COMP", "Menu"},
    };

    private static final Map<Character, String> NAMES = new HashMap<>();
    static {
        String p = "!exclam \"quotedbl #numbersign $dollar %percent &ampersand 'apostrophe (parenleft )parenright *asterisk +plus ,comma -minus .period /slash "
                + ":colon ;semicolon <less =equal >greater ?question @at [bracketleft \\backslash ]bracketright ^asciicircum _underscore `grave "
                + "{braceleft |bar }braceright ~asciitilde";
        for (String t : p.split(" ")) NAMES.put(t.charAt(0), t.substring(1));
        String[] cyr = {
            "\u0439 Cyrillic_shorti", "\u0419 Cyrillic_SHORTI", "\u0446 Cyrillic_tse", "\u0426 Cyrillic_TSE",
            "\u0443 Cyrillic_u", "\u0423 Cyrillic_U", "\u043A Cyrillic_ka", "\u041A Cyrillic_KA",
            "\u0435 Cyrillic_ie", "\u0415 Cyrillic_IE", "\u043D Cyrillic_en", "\u041D Cyrillic_EN",
            "\u0433 Cyrillic_ghe", "\u0413 Cyrillic_GHE", "\u0448 Cyrillic_sha", "\u0428 Cyrillic_SHA",
            "\u0449 Cyrillic_shcha", "\u0429 Cyrillic_SHCHA", "\u0437 Cyrillic_ze", "\u0417 Cyrillic_ZE",
            "\u0445 Cyrillic_ha", "\u0425 Cyrillic_HA", "\u0457 Ukrainian_yi", "\u0407 Ukrainian_YI",
            "\u0444 Cyrillic_ef", "\u0424 Cyrillic_EF", "\u0456 Ukrainian_i", "\u0406 Ukrainian_I",
            "\u0432 Cyrillic_ve", "\u0412 Cyrillic_VE", "\u0430 Cyrillic_a", "\u0410 Cyrillic_A",
            "\u043F Cyrillic_pe", "\u041F Cyrillic_PE", "\u0440 Cyrillic_er", "\u0420 Cyrillic_ER",
            "\u043E Cyrillic_o", "\u041E Cyrillic_O", "\u043B Cyrillic_el", "\u041B Cyrillic_EL",
            "\u0434 Cyrillic_de", "\u0414 Cyrillic_DE", "\u0436 Cyrillic_zhe", "\u0416 Cyrillic_ZHE",
            "\u0454 Ukrainian_ie", "\u0404 Ukrainian_IE", "\u0491 Ukrainian_ghe_with_upturn", "\u0490 Ukrainian_GHE_WITH_UPTURN",
            "\u044F Cyrillic_ya", "\u042F Cyrillic_YA", "\u0447 Cyrillic_che", "\u0427 Cyrillic_CHE",
            "\u0441 Cyrillic_es", "\u0421 Cyrillic_ES", "\u043C Cyrillic_em", "\u041C Cyrillic_EM",
            "\u0438 Cyrillic_i", "\u0418 Cyrillic_I", "\u0442 Cyrillic_te", "\u0422 Cyrillic_TE",
            "\u044C Cyrillic_softsign", "\u042C Cyrillic_SOFTSIGN", "\u0431 Cyrillic_be", "\u0411 Cyrillic_BE",
            "\u044E Cyrillic_yu", "\u042E Cyrillic_YU", "\u2116 numerosign",
        };
        for (String t : cyr) NAMES.put(t.charAt(0), t.substring(2));
    }

    static String keysym(String ch) {
        char c = ch.charAt(0);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return ch;
        String n = NAMES.get(c);
        return n != null ? n : ch;
    }

    private static final Map<Character, Hit> LAYOUT0 = new HashMap<>(), LAYOUT1 = new HashMap<>();
    static {
        for (String[] r : CHARS) {
            int code = Integer.parseInt(r[0]);
            LAYOUT0.put(r[4].charAt(0), new Hit(code, 0, false));
            LAYOUT0.put(r[5].charAt(0), new Hit(code, 0, true));
            LAYOUT1.put(r[6].charAt(0), new Hit(code, 1, false));
            LAYOUT1.put(r[7].charAt(0), new Hit(code, 1, true));
        }
        for (char c : new char[]{' ', '\n', '\t'}) {
            int code = c == ' ' ? 57 : c == '\n' ? 28 : 15;
            LAYOUT0.put(c, new Hit(code, 0, false));
            LAYOUT1.put(c, new Hit(code, 1, false));
        }
    }

    /** Клавіша для символу: спочатку в поточній групі, потім в іншій. null — символу немає в таблиці. */
    public static Hit find(char c, int currentGroup) {
        Hit h = (currentGroup == 1 ? LAYOUT1 : LAYOUT0).get(c);
        if (h == null) h = (currentGroup == 1 ? LAYOUT0 : LAYOUT1).get(c);
        return h;
    }

    private static String typeName(String t) { return t.equals("A") ? "ALPHABETIC" : t.equals("T") ? "TWO_LEVEL" : "ONE_LEVEL"; }

    /** Повний xkb_keymap (text v1). */
    public static String keymap() {
        StringBuilder b = new StringBuilder(16384);
        b.append("xkb_keymap {\n");
        b.append("xkb_keycodes \"kate\" {\n\tminimum = 8;\n\tmaximum = 255;\n");
        for (String[] r : CHARS) b.append("\t<").append(r[1]).append("> = ").append(Integer.parseInt(r[0]) + 8).append(";\n");
        b.append("\t<TAB> = 23;\n");
        for (String[] r : FUNC) b.append("\t<").append(r[1]).append("> = ").append(Integer.parseInt(r[0]) + 8).append(";\n");
        b.append("};\n");
        b.append("xkb_types \"kate\" {\n");
        b.append("\ttype \"ONE_LEVEL\" {\n\t\tmodifiers = none;\n\t\tlevel_name[Level1] = \"Any\";\n\t};\n");
        b.append("\ttype \"TWO_LEVEL\" {\n\t\tmodifiers = Shift;\n\t\tmap[Shift] = Level2;\n\t\tlevel_name[Level1] = \"Base\";\n\t\tlevel_name[Level2] = \"Shift\";\n\t};\n");
        b.append("\ttype \"ALPHABETIC\" {\n\t\tmodifiers = Shift+Lock;\n\t\tmap[Shift] = Level2;\n\t\tmap[Lock] = Level2;\n\t\tlevel_name[Level1] = \"Base\";\n\t\tlevel_name[Level2] = \"Caps\";\n\t};\n");
        b.append("};\n");
        b.append("xkb_compat \"kate\" {\n\tinterpret.useModMapMods = AnyLevel;\n\tinterpret.repeat = False;\n};\n");
        b.append("xkb_symbols \"kate\" {\n\tname[Group1] = \"English (US)\";\n\tname[Group2] = \"Ukrainian\";\n");
        for (String[] r : CHARS) {
            b.append("\tkey <").append(r[1]).append("> {\n");
            b.append("\t\ttype[Group1] = \"").append(typeName(r[2])).append("\",\n");
            b.append("\t\ttype[Group2] = \"").append(typeName(r[3])).append("\",\n");
            b.append("\t\tsymbols[Group1] = [ ").append(keysym(r[4])).append(", ").append(keysym(r[5])).append(" ],\n");
            b.append("\t\tsymbols[Group2] = [ ").append(keysym(r[6])).append(", ").append(keysym(r[7])).append(" ]\n\t};\n");
        }
        b.append("\tkey <TAB> {\n\t\ttype[Group1] = \"TWO_LEVEL\",\n\t\ttype[Group2] = \"TWO_LEVEL\",\n\t\tsymbols[Group1] = [ Tab, ISO_Left_Tab ],\n\t\tsymbols[Group2] = [ Tab, ISO_Left_Tab ]\n\t};\n");
        for (String[] r : FUNC) {
            b.append("\tkey <").append(r[1]).append("> {\n\t\ttype[Group1] = \"ONE_LEVEL\",\n\t\ttype[Group2] = \"ONE_LEVEL\",\n");
            b.append("\t\tsymbols[Group1] = [ ").append(r[2]).append(" ],\n\t\tsymbols[Group2] = [ ").append(r[2]).append(" ]\n\t};\n");
        }
        b.append("\tmodifier_map Shift { <LFSH>, <RTSH> };\n\tmodifier_map Lock { <CAPS> };\n\tmodifier_map Control { <LCTL>, <RCTL> };\n");
        b.append("\tmodifier_map Mod1 { <LALT>, <RALT> };\n\tmodifier_map Mod2 { <NMLK> };\n\tmodifier_map Mod4 { <LWIN>, <RWIN> };\n");
        b.append("};\n};\n");
        return b.toString();
    }

    public static void main(String[] a) throws Exception {
        java.io.PrintStream out = a.length > 0 ? new java.io.PrintStream(a[0], "UTF-8") : System.out;
        out.print(keymap());
    }
}
