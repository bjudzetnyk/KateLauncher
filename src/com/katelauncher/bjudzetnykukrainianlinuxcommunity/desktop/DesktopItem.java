package com.gnome.launcher.desktop;

public final class DesktopItem {
    public final String key;
    public int col, row;      // збережена (логічна) позиція
    public int dc = -1, dr = -1; // позиція на екрані зараз (не зберігається)
    String shown;
    String shownSrc;
    float shownW;

    public DesktopItem(String key, int col, int row) {
        this.key = key;
        this.col = col;
        this.row = row;
    }
}
