/* KateLauncher Wayland compositor core (KWL).
 * Мінімальний Wayland-композитор без залежностей від libwayland: власний розбір протоколу.
 * Ядро не знає про Android/JNI: усе назовні йде через KwlHost, усе всередину — через kwl_cmd().
 * Протоколи: wl_compositor, wl_subcompositor, wl_shm, wl_output, wl_seat (pointer+keyboard),
 * wl_data_device_manager, xdg_wm_base (toplevel+popup), zxdg_decoration_manager_v1,
 * zwp_relative_pointer_manager_v1, zwp_pointer_constraints_v1; окремо — менеджер вікон X11 (XWM) для Xwayland. */
#ifndef KWL_H
#define KWL_H
#include <stdint.h>

typedef struct Kwl Kwl;

/* Колбеки викликаються з потоку композитора (kwl_run). Рядки дійсні лише на час виклику. */
typedef struct {
    /* Вікно з'явилось (перший буфер). kind: 0 — toplevel, 1 — popup Wayland (parent, px, py у логічних px батька),
     * 2 — popup X11 (override-redirect: меню/підказка; закривається самою програмою, клік поза ним НЕ «з'їдається»). */
    void (*window_add)(void *ud, int id, int kind, int parent, int px, int py, int w, int h, int scale,
                       const char *app_tag, const char *app_id, const char *title, int decorated);
    /* Нова спільна область пікселів (RGBA premultiplied, stride = w*4, w,h — у пікселях буфера).
     * fd дійсний лише на час виклику — одержувач має зробити dup. */
    void (*window_buffer)(void *ud, int id, int gen, int w, int h, int scale, int fd, int size);
    void (*window_damage)(void *ud, int id, int gen, int x, int y, int w, int h);
    void (*window_title)(void *ud, int id, const char *title);
    void (*window_appid)(void *ud, int id, const char *app_id);
    void (*window_hints)(void *ud, int id, int minw, int minh, int maxw, int maxh);
    void (*window_remove)(void *ud, int id);
    void (*window_request)(void *ud, int id, int kind, int arg);
    void (*window_popup)(void *ud, int id, int px, int py, int w, int h);
    void (*clipboard)(void *ud, const char *utf8);
    void (*quit)(void *ud);
} KwlHost;

/* kwl_request kinds */
enum { KWL_REQ_MOVE = 1, KWL_REQ_RESIZE = 2, KWL_REQ_MAXIMIZE = 3, KWL_REQ_MINIMIZE = 4,
       KWL_REQ_FULLSCREEN = 5, KWL_REQ_WANT_CLOSE_POPUPS = 6 };

/* kwl_cmd ops (можна викликати з будь-якого потоку) */
enum {
    KWL_CMD_POINTER_MOVE = 1,   /* a=win, x,y — координати в логічних px вікна */
    KWL_CMD_POINTER_BUTTON,     /* a=win, b=linux button code, c=1 pressed/0 released, x,y */
    KWL_CMD_POINTER_AXIS,       /* a=win, b=axis(0 vert,1 horiz), c=discrete steps, x=value (px) */
    KWL_CMD_POINTER_LEAVE,
    KWL_CMD_KEY,                /* a=evdev code, b=1 pressed/0 released */
    KWL_CMD_GROUP,              /* a=layout group */
    KWL_CMD_FOCUS,              /* a=win (0 — жодне) */
    KWL_CMD_CONFIGURE,          /* a=win, b=w, c=h (логічні), d=flags: 1 maximized, 2 fullscreen, 4 resizing */
    KWL_CMD_CLOSE,              /* a=win */
    KWL_CMD_VISIBLE,            /* a=win, b=0/1 */
    KWL_CMD_OUTPUT,             /* a=w, b=h (логічні), c=scale */
    KWL_CMD_FPS,                /* a=fps */
    KWL_CMD_CLIPBOARD,          /* s=text (UTF-8) */
    KWL_CMD_DISMISS_POPUPS,
    KWL_CMD_STOP,
    KWL_CMD_XWM,                /* s=шлях до unix-сокета X-сервера (Xwayland): увімкнути вбудований менеджер вікон X11 */
    KWL_CMD_XWM_STOP,           /* відключитись від X-сервера */
    KWL_CMD_DEBUG,              /* a=1: докладний журнал (вхід, фокус, X11) у stderr */
    KWL_CMD_IDLE,               /* a=мс простою без вікон до самозавершення (0 — ніколи) */
    KWL_CMD_ACTIVITY,           /* програма запускається (a=секунд «пільгового» часу, щоб не завершитись) */
    KWL_CMD_RESYNC              /* лаунчер перепідключився: повторно оголосити всі вікна */
};

/* dir/sock: каталог і ім'я сокета (напр. $TMP/katewl, "wayland-kate"). keymap — текст xkb_keymap. */
Kwl *kwl_create(const KwlHost *host, void *ud, const char *dir, const char *sock, const char *keymap,
                int out_w, int out_h, int scale, int fps);
void kwl_run(Kwl *k);                 /* блокує до kwl_cmd(STOP) або idle-виходу */
void kwl_cmd(Kwl *k, int op, int a, int b, int c, int d, double x, double y, const char *s);
void kwl_destroy(Kwl *k);             /* після kwl_run */

#endif
