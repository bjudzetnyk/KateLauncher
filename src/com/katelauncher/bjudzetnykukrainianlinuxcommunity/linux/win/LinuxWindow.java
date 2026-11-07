package com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.win;

/**
 * Абстракція «вікно з буфером». Менеджер вікон (LinuxWm) працює ЛИШЕ з нею і не знає, звідки вікно
 * (Wayland-композитор, тестове джерело чи щось інше).
 * Усі розміри — у логічних пікселях (як їх бачить Linux-програма); множення на zoom робить менеджер.
 */
public interface LinuxWindow {
    int STATE_NORMAL = 0, STATE_MINIMIZED = 1, STATE_MAXIMIZED = 2;

    int id();
    /** Ключ програми лаунчера ("linux/<id>"); null, якщо програму визначити не вдалося. */
    String appKey();
    String title();
    int width();
    int height();
    /** Масштаб буфера (buffer_scale): пікселів буфера на 1 логічний піксель. */
    int scale();
    /** Буфер кадру (плитки Bitmap). Може бути null, доки немає першого кадру. */
    LinuxBuffer buffer();
    /** Стан задає менеджер вікон. */
    int state();
    void setState(int state);

    boolean isPopup();
    /** Popup X11 (меню/підказка): програма закриває його сама, тож клік поза ним не «з'їдається» лаунчером. */
    boolean isForeignPopup();
    LinuxWindow parent();
    int popupX();
    int popupY();
    /** true — рамку малює лаунчер (SSD). false — програма малює власну (CSD). */
    boolean serverDecorated();
    int minWidth();
    int minHeight();

    // --- Керування ---
    void requestSize(int w, int h, boolean maximized, boolean resizing);
    void requestClose();
    void setVisible(boolean visible);
    /** Передати клавіатурний фокус цьому вікну (null-фокус задається через WindowSource.setFocus(null)). */
    void focus();
    void dismissPopups();

    // --- Введення (координати — логічні, у системі вікна) ---
    void pointerMove(float x, float y);
    void pointerButton(int linuxButton, boolean down, float x, float y);
    void pointerAxis(int axis, float value, int discrete);
    void pointerLeave();
    void key(int evdev, boolean down);
    void setGroup(int group);

    WindowSource source();
}
