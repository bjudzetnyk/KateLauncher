package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux;

/** Linux-середовище: назва + команда входу (напр. "proot-distro login ubuntu --"). Зберігається як ввів користувач. */
public final class LinuxEnv {
    public final String id;
    public String name, command;
    /** "" — як у налаштуваннях за замовчуванням, "wayland" або "x11". */
    public String display = "";
    public LinuxEnv(String id, String name, String command) { this.id = id; this.name = name; this.command = command; }
}
