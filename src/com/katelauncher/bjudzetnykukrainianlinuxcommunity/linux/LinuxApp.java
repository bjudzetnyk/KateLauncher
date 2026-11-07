package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

/** Встановлена Linux-програма. Команда запуску зберігається й передається дослівно. */
public final class LinuxApp {
    public static final String PKG = "linux";
    public final String id;
    public String name, envId, command;
    public boolean customIcon;
    /** "" — як у середовища, "wayland" або "x11". */
    public String display = "";
    public LinuxApp(String id, String name, String envId, String command, boolean customIcon) {
        this.id = id; this.name = name; this.envId = envId; this.command = command; this.customIcon = customIcon;
    }
    public String key() { return PKG + "/" + id; }
}
