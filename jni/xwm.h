/* XWM — мінімальний менеджер вікон X11 усередині композитора (включається в kwl.c).
 *
 * Навіщо. Xwayland у режимі -rootless віддає композитору кожне вікно X11 як звичайний wl_surface,
 * але лише тоді, коли «менеджер вікон» X11 перехопив керування (SubstructureRedirect) і ввімкнув
 * Composite. Тому композитор сам підключається до X-сервера як його менеджер вікон. Протокол X11
 * розбирається вручну (без libxcb), як і Wayland у kwl.c.
 *
 * Що робить:
 *  - перехоплює MapRequest/ConfigureRequest, виставляє EWMH (_NET_SUPPORTED, _NET_SUPPORTING_WM_CHECK…);
 *  - зв'язує вікно X11 із поверхнею Wayland через клієнтське повідомлення WL_SURFACE_ID;
 *  - заголовок, клас, підказки розміру, WM_DELETE_WINDOW, повноекранний/максимізований стан;
 *  - вікна override-redirect (меню, підказки) стають popup-ами вікна-власника;
 *  - фокус: X-фокус ставиться синхронно з клавіатурним фокусом Wayland.
 *
 * Розташування. Усі вікна лаунчера — окремі плаваючі вікна Android, тож у координатах X вони
 * не мають збігатися (інакше X вибере «верхнє» вікно під курсором, а не те, на яке натиснули).
 * Тому АКТИВНЕ вікно стоїть у (0,0) (всередині «монітора», щоб меню не обрізались), а решта
 * «припарковані» у далеких слотах, що не перетинаються. */

/* ---------- атоми ---------- */
enum {
    A_WM_PROTOCOLS, A_WM_DELETE, A_WM_TAKE_FOCUS, A_WM_STATE, A_WM_CHANGE_STATE, A_WL_SURFACE_ID,
    A_NET_SUPPORTED, A_NET_CHECK, A_NET_WM_NAME, A_UTF8, A_NET_WM_STATE, A_NET_FS, A_NET_MAXV, A_NET_MAXH,
    A_NET_FOCUSED, A_NET_HIDDEN, A_NET_ACTIVE, A_NET_CLOSE, A_NET_MOVERESIZE, A_NET_WM_PID, A_NET_FRAME,
    A_NET_CLIENT_LIST, A_NET_WTYPE, A_WT_DIALOG, A_WT_MENU, A_WT_DROPDOWN, A_WT_POPUP, A_WT_TOOLTIP,
    A_WT_COMBO, A_WT_DND, A_WT_NOTIF, A_WT_SPLASH, A_WT_UTILITY, A_MOTIF, A_NET_REQ_FRAME, A_NET_DESKTOP,
    A_NET_NDESK, A_NET_CURDESK, A_NET_WORKAREA, A_NET_MOVERESIZE_WINDOW, A_COUNT
};
static const char *const XANAMES[A_COUNT] = {
    "WM_PROTOCOLS", "WM_DELETE_WINDOW", "WM_TAKE_FOCUS", "WM_STATE", "WM_CHANGE_STATE", "WL_SURFACE_ID",
    "_NET_SUPPORTED", "_NET_SUPPORTING_WM_CHECK", "_NET_WM_NAME", "UTF8_STRING", "_NET_WM_STATE",
    "_NET_WM_STATE_FULLSCREEN", "_NET_WM_STATE_MAXIMIZED_VERT", "_NET_WM_STATE_MAXIMIZED_HORZ",
    "_NET_WM_STATE_FOCUSED", "_NET_WM_STATE_HIDDEN", "_NET_ACTIVE_WINDOW", "_NET_CLOSE_WINDOW",
    "_NET_WM_MOVERESIZE", "_NET_WM_PID", "_NET_FRAME_EXTENTS", "_NET_CLIENT_LIST", "_NET_WM_WINDOW_TYPE",
    "_NET_WM_WINDOW_TYPE_DIALOG", "_NET_WM_WINDOW_TYPE_MENU", "_NET_WM_WINDOW_TYPE_DROPDOWN_MENU",
    "_NET_WM_WINDOW_TYPE_POPUP_MENU", "_NET_WM_WINDOW_TYPE_TOOLTIP", "_NET_WM_WINDOW_TYPE_COMBO",
    "_NET_WM_WINDOW_TYPE_DND", "_NET_WM_WINDOW_TYPE_NOTIFICATION", "_NET_WM_WINDOW_TYPE_SPLASH",
    "_NET_WM_WINDOW_TYPE_UTILITY", "_MOTIF_WM_HINTS", "_NET_REQUEST_FRAME_EXTENTS", "_NET_WM_DESKTOP",
    "_NET_NUMBER_OF_DESKTOPS", "_NET_CURRENT_DESKTOP", "_NET_WORKAREA", "_NET_MOVERESIZE_WINDOW"
};
/* готові атоми X11 */
enum { XA_ATOM = 4, XA_CARDINAL = 6, XA_STRING = 31, XA_WINDOW = 33, XA_WM_HINTS = 35, XA_WM_NAME = 39,
       XA_WM_NORMAL_HINTS = 40, XA_WM_CLASS = 67, XA_WM_TRANSIENT_FOR = 68 };

#define XWM_PARK_X 6000
#define XWM_PARK_Y 0
#define XWM_PARK_PITCH 5000

struct XWin {
    uint32_t id; int x, y, w, h;
    int or_, mapped, managed;
    uint32_t surf, pend_surf; Window *win;
    char *title, *cls; int has_delete, take_focus, input;
    int minw, minh, maxw, maxh; int nodeco; uint32_t trans; int popup_type;
    int fs, maxd; int pid; char tag[64]; int slot; int pend_req;
    XWin *next;
};

struct Xwm {
    Kwl *k; int fd; uint32_t base, mask, shift, root, nextid; uint16_t seq;
    uint32_t atoms[A_COUNT]; uint32_t check_win; int composite_op;
    uint8_t *rb; size_t rl, rc;
    uint8_t (*evq)[32]; int nevq, evcap;
    XWin *wins, *origin; int xpid; int ready; int broken;
    uint32_t last_focus_req;
};

static void xwm_close_conn(Kwl *k);

/* ---------- запис запитів ---------- */
typedef struct { uint8_t b[2048]; int n; } XB;
static void xb_init(XB *q, int op, int data) { memset(q->b, 0, 8); q->b[0] = (uint8_t)op; q->b[1] = (uint8_t)data; q->n = 4; }
static void xb_u8(XB *q, uint32_t v) { if (q->n < (int)sizeof q->b) q->b[q->n++] = (uint8_t)v; }
static void xb_u16(XB *q, uint32_t v) { xb_u8(q, v & 255); xb_u8(q, (v >> 8) & 255); }
static void xb_u32(XB *q, uint32_t v) { xb_u16(q, v & 0xffff); xb_u16(q, v >> 16); }
static void xb_bytes(XB *q, const void *d, int n) { for (int i = 0; i < n; i++) xb_u8(q, ((const uint8_t *)d)[i]); while (q->n & 3) xb_u8(q, 0); }

static int xw_write_all(Xwm *x, const uint8_t *p, size_t n) {
    while (n) {
        ssize_t r = send(x->fd, p, n, MSG_NOSIGNAL);
        if (r < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN) { struct pollfd pf = { x->fd, POLLOUT, 0 }; if (poll(&pf, 1, 1000) <= 0) return -1; continue; }
            return -1;
        }
        p += r; n -= (size_t)r;
    }
    return 0;
}

/* повертає номер послідовності запиту (0 — помилка) */
static uint16_t xb_send(Xwm *x, XB *q) {
    while (q->n & 3) xb_u8(q, 0);
    uint16_t len = (uint16_t)(q->n / 4);
    q->b[2] = len & 255; q->b[3] = len >> 8;
    if (x->broken || xw_write_all(x, q->b, (size_t)q->n) < 0) { x->broken = 1; return 0; }
    return ++x->seq;
}

/* ---------- читання ---------- */
static int xw_rd(Xwm *x, int timeout_ms) {
    if (timeout_ms >= 0) { struct pollfd pf = { x->fd, POLLIN, 0 }; int r = poll(&pf, 1, timeout_ms); if (r < 0) return errno == EINTR ? 0 : -1; if (r == 0) return 0; }
    if (x->rc - x->rl < 4096) { size_t nc = x->rc ? x->rc * 2 : 16384; uint8_t *t = realloc(x->rb, nc); if (!t) return -1; x->rb = t; x->rc = nc; }
    ssize_t n = recv(x->fd, x->rb + x->rl, x->rc - x->rl, MSG_DONTWAIT);
    if (n < 0) return (errno == EAGAIN || errno == EINTR) ? 0 : -1;
    if (n == 0) return -1;
    x->rl += (size_t)n;
    return 1;
}
static uint16_t rd16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }
static uint32_t rd32(const uint8_t *p) { return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24); }
static void wr16(uint8_t *p, uint32_t v) { p[0] = v & 255; p[1] = (v >> 8) & 255; }
static void wr32(uint8_t *p, uint32_t v) { wr16(p, v & 0xffff); wr16(p + 2, v >> 16); }

/* розмір першого повного пакета в буфері; 0 — ще немає */
static size_t xw_pktlen(Xwm *x) {
    if (x->rl < 32) return 0;
    uint8_t t = x->rb[0];
    size_t n = 32;
    if (t == 1 || (t & 0x7f) == 35) n += (size_t)rd32(x->rb + 4) * 4;
    return x->rl >= n ? n : 0;
}
static void xw_consume(Xwm *x, size_t n) { memmove(x->rb, x->rb + n, x->rl - n); x->rl -= n; }
static void xw_queue_ev(Xwm *x, const uint8_t *e) {
    if (x->nevq == x->evcap) { x->evcap = x->evcap ? x->evcap * 2 : 32; x->evq = realloc(x->evq, (size_t)x->evcap * 32); }
    memcpy(x->evq[x->nevq++], e, 32);
}

/* Чекає відповідь на запит seq; сторонні події складає в чергу. Повертає malloc-пакет або NULL. */
static uint8_t *xw_wait(Xwm *x, uint16_t seq, size_t *outlen) {
    if (!seq) return NULL;
    uint32_t t0 = now_ms();
    for (;;) {
        size_t n;
        while ((n = xw_pktlen(x))) {
            uint8_t t = x->rb[0];
            if (t == 0 || t == 1) {
                if (rd16(x->rb + 2) == seq) {
                    uint8_t *r = malloc(n); memcpy(r, x->rb, n); xw_consume(x, n);
                    if (t == 0) { LOG("xwm: помилка X11 code=%u (запит %u, op=%u, id=0x%x)", r[1], seq, r[10], rd32(r + 4)); free(r); return NULL; }
                    if (outlen) *outlen = n;
                    return r;
                }
                xw_consume(x, n);
            } else { xw_queue_ev(x, x->rb); xw_consume(x, n); }
        }
        int left = 1500 - (int)(now_ms() - t0);
        if (left <= 0) { LOG("xwm: таймаут відповіді %u", seq); return NULL; }
        int r = xw_rd(x, left);
        if (r < 0) { x->broken = 1; return NULL; }
    }
}

/* ---------- прості запити ---------- */
static void xw_change_prop(Xwm *x, uint32_t win, uint32_t prop, uint32_t type, int fmt, const void *data, int nunits) {
    XB q; xb_init(&q, 18, 0); xb_u32(&q, win); xb_u32(&q, prop); xb_u32(&q, type); xb_u8(&q, (uint32_t)fmt); xb_u8(&q, 0); xb_u16(&q, 0);
    xb_u32(&q, (uint32_t)nunits);
    xb_bytes(&q, data, nunits * (fmt / 8));
    xb_send(x, &q);
}
static void xw_set_cardinals(Xwm *x, uint32_t win, uint32_t prop, uint32_t type, const uint32_t *v, int n) {
    uint8_t buf[256]; if (n > 60) n = 60;
    for (int i = 0; i < n; i++) wr32(buf + i * 4, v[i]);
    xw_change_prop(x, win, prop, type, 32, buf, n);
}
static void xw_delete_prop(Xwm *x, uint32_t win, uint32_t prop) { XB q; xb_init(&q, 19, 0); xb_u32(&q, win); xb_u32(&q, prop); xb_send(x, &q); }

/* GetProperty: повертає malloc-значення (завжди +1 нуль-байт), довжину в байтах */
static uint8_t *xw_get_prop(Xwm *x, uint32_t win, uint32_t prop, uint32_t type, uint32_t maxlongs, size_t *nbytes, int *fmt, uint32_t *atype) {
    XB q; xb_init(&q, 20, 0); xb_u32(&q, win); xb_u32(&q, prop); xb_u32(&q, type); xb_u32(&q, 0); xb_u32(&q, maxlongs);
    size_t n; uint8_t *r = xw_wait(x, xb_send(x, &q), &n); if (!r) return NULL;
    int f = r[1]; uint32_t units = rd32(r + 16); uint32_t at = rd32(r + 8);
    size_t bytes = (size_t)units * (size_t)(f / 8);
    if (f == 0 || bytes == 0 || 32 + bytes > n) { free(r); return NULL; }
    uint8_t *v = malloc(bytes + 1); memcpy(v, r + 32, bytes); v[bytes] = 0; free(r);
    if (nbytes) *nbytes = bytes; if (fmt) *fmt = f; if (atype) *atype = at;
    return v;
}

static void xw_configure(Xwm *x, uint32_t win, uint32_t mask, const int32_t *vals) {
    XB q; xb_init(&q, 12, 0); xb_u32(&q, win); xb_u16(&q, mask); xb_u16(&q, 0);
    for (int i = 0, j = 0; i < 7; i++) if (mask & (1u << i)) xb_u32(&q, (uint32_t)vals[j++]);
    xb_send(x, &q);
}
static void xw_move_resize(Xwm *x, XWin *xw, int px, int py, int w, int h) {
    int32_t v[4] = { px, py, w, h }; uint32_t m = 3;
    if (w > 0 && h > 0) m |= 12;
    xw_configure(x, xw->id, m, v);
    xw->x = px; xw->y = py; if (w > 0 && h > 0) { xw->w = w; xw->h = h; }
}
static void xw_map(Xwm *x, uint32_t win) { XB q; xb_init(&q, 8, 0); xb_u32(&q, win); xb_send(x, &q); }

static void xw_send_event(Xwm *x, uint32_t dst, uint32_t mask, const uint8_t *ev32) {
    XB q; xb_init(&q, 25, 0); xb_u32(&q, dst); xb_u32(&q, mask); xb_bytes(&q, ev32, 32); xb_send(x, &q);
}
static void xw_client_msg(Xwm *x, uint32_t dst, uint32_t win, uint32_t type, uint32_t d0, uint32_t d1, uint32_t d2) {
    uint8_t ev[32]; memset(ev, 0, sizeof ev);
    ev[0] = 33; ev[1] = 32; wr32(ev + 4, win); wr32(ev + 8, type); wr32(ev + 12, d0); wr32(ev + 16, d1); wr32(ev + 20, d2);
    xw_send_event(x, dst, 0, ev);
}
static void xw_sync(Xwm *x) { XB q; xb_init(&q, 43, 0); uint8_t *r = xw_wait(x, xb_send(x, &q), NULL); free(r); }  /* GetInputFocus */

static void xw_synth_configure(Xwm *x, XWin *xw) {
    uint8_t ev[32]; memset(ev, 0, sizeof ev);
    ev[0] = 22; wr32(ev + 4, xw->id); wr32(ev + 8, xw->id); wr32(ev + 12, 0);
    wr16(ev + 16, (uint32_t)xw->x); wr16(ev + 18, (uint32_t)xw->y); wr16(ev + 20, (uint32_t)xw->w); wr16(ev + 22, (uint32_t)xw->h);
    xw_send_event(x, xw->id, 0x20000 /* StructureNotify */, ev);
}

/* ---------- облік вікон ---------- */
static XWin *xw_find(Xwm *x, uint32_t id) { for (XWin *w = x->wins; w; w = w->next) if (w->id == id) return w; return NULL; }
static XWin *xw_get(Xwm *x, uint32_t id) {
    XWin *w = xw_find(x, id); if (w) return w;
    w = calloc(1, sizeof *w); w->id = id; w->input = 1; w->slot = -1; w->next = x->wins; x->wins = w; return w;
}
static void xw_free(Xwm *x, XWin *xw) {
    for (XWin **pp = &x->wins; *pp; pp = &(*pp)->next) if (*pp == xw) { *pp = xw->next; break; }
    if (x->origin == xw) x->origin = NULL;
    if (xw->win) xw->win->xw = NULL;
    free(xw->title); free(xw->cls); free(xw);
}

static void xw_park_pos(int slot, int *px, int *py) { *px = XWM_PARK_X + (slot % 5) * XWM_PARK_PITCH; *py = XWM_PARK_Y + (slot / 5) * XWM_PARK_PITCH; }

static int xw_free_slot(Xwm *x) {
    for (int s = 0; s < 30; s++) {
        int used = 0;
        for (XWin *w = x->wins; w; w = w->next) if (w->managed && w->slot == s) { used = 1; break; }
        if (!used) return s;
    }
    return 0;
}

/* зробити xw активним: він у (0,0), попереднє активне — у свій слот */
static void xw_activate(Xwm *x, XWin *xw) {
    if (!xw || !xw->managed || x->origin == xw) return;
    XWin *old = x->origin;
    if (old && old->managed) { int px, py; xw_park_pos(old->slot, &px, &py); xw_move_resize(x, old, px, py, 0, 0); xw_synth_configure(x, old); }
    x->origin = xw;
    xw_move_resize(x, xw, 0, 0, 0, 0);
    xw_synth_configure(x, xw);
}

static char *xw_latin1_to_utf8(const uint8_t *s, size_t n) {
    char *o = malloc(n * 2 + 1); size_t j = 0;
    for (size_t i = 0; i < n; i++) { if (s[i] < 0x80) o[j++] = (char)s[i]; else { o[j++] = (char)(0xC0 | (s[i] >> 6)); o[j++] = (char)(0x80 | (s[i] & 0x3f)); } }
    o[j] = 0; return o;
}

static void xw_read_title(Xwm *x, XWin *xw) {
    size_t n; int f; uint32_t t;
    uint8_t *v = xw_get_prop(x, xw->id, x->atoms[A_NET_WM_NAME], 0, 256, &n, &f, &t);
    char *s = NULL;
    if (v && f == 8) s = strdup((char *)v);
    else { free(v); v = xw_get_prop(x, xw->id, XA_WM_NAME, 0, 256, &n, &f, &t); if (v && f == 8) s = (t == x->atoms[A_UTF8]) ? strdup((char *)v) : xw_latin1_to_utf8(v, n); }
    free(v);
    free(xw->title); xw->title = s;
}

static void xw_read_class(Xwm *x, XWin *xw) {
    size_t n; int f; uint32_t t;
    uint8_t *v = xw_get_prop(x, xw->id, XA_WM_CLASS, XA_STRING, 64, &n, &f, &t);
    free(xw->cls); xw->cls = NULL;
    if (v && f == 8) {
        const char *inst = (char *)v; size_t li = strlen(inst);
        const char *cl = li + 1 < n ? inst + li + 1 : inst;
        xw->cls = strdup(*cl ? cl : inst);
    }
    free(v);
}

static void xw_read_hints(Xwm *x, XWin *xw) {
    size_t n; int f; uint32_t t;
    uint8_t *v = xw_get_prop(x, xw->id, XA_WM_NORMAL_HINTS, 0, 18, &n, &f, &t);
    xw->minw = xw->minh = xw->maxw = xw->maxh = 0;
    if (v && f == 32 && n >= 36) {
        uint32_t fl = rd32(v);
        if (fl & 16) { xw->minw = (int)rd32(v + 20); xw->minh = (int)rd32(v + 24); }
        if (fl & 32) { xw->maxw = (int)rd32(v + 28); xw->maxh = (int)rd32(v + 32); }
        if (xw->maxw >= 32767) xw->maxw = 0;
        if (xw->maxh >= 32767) xw->maxh = 0;
    }
    free(v);
    xw->input = 1;
    v = xw_get_prop(x, xw->id, XA_WM_HINTS, 0, 9, &n, &f, &t);
    if (v && f == 32 && n >= 8 && (rd32(v) & 1)) xw->input = (int)rd32(v + 4) != 0;
    free(v);
}

static void xw_read_protocols(Xwm *x, XWin *xw) {
    size_t n; int f; uint32_t t;
    uint8_t *v = xw_get_prop(x, xw->id, x->atoms[A_WM_PROTOCOLS], XA_ATOM, 32, &n, &f, &t);
    xw->has_delete = xw->take_focus = 0;
    if (v && f == 32) for (size_t i = 0; i + 4 <= n; i += 4) {
        uint32_t a = rd32(v + i);
        if (a == x->atoms[A_WM_DELETE]) xw->has_delete = 1;
        if (a == x->atoms[A_WM_TAKE_FOCUS]) xw->take_focus = 1;
    }
    free(v);
}

static void xw_read_misc(Xwm *x, XWin *xw) {
    size_t n; int f; uint32_t t;
    uint8_t *v = xw_get_prop(x, xw->id, x->atoms[A_NET_WM_PID], XA_CARDINAL, 1, &n, &f, &t);
    if (v && f == 32 && n >= 4) xw->pid = (int)rd32(v);
    free(v);
    xw->nodeco = 0;
    v = xw_get_prop(x, xw->id, x->atoms[A_MOTIF], 0, 5, &n, &f, &t);
    if (v && f == 32 && n >= 12) { uint32_t fl = rd32(v), deco = rd32(v + 8); if ((fl & 2) && deco == 0) xw->nodeco = 1; }
    free(v);
    xw->popup_type = 0;
    v = xw_get_prop(x, xw->id, x->atoms[A_NET_WTYPE], XA_ATOM, 8, &n, &f, &t);
    if (v && f == 32) for (size_t i = 0; i + 4 <= n; i += 4) {
        uint32_t a = rd32(v + i);
        if (a == x->atoms[A_WT_MENU] || a == x->atoms[A_WT_DROPDOWN] || a == x->atoms[A_WT_POPUP] || a == x->atoms[A_WT_TOOLTIP] ||
            a == x->atoms[A_WT_COMBO] || a == x->atoms[A_WT_DND] || a == x->atoms[A_WT_NOTIF]) xw->popup_type = 1;
    }
    free(v);
    xw->trans = 0;
    v = xw_get_prop(x, xw->id, XA_WM_TRANSIENT_FOR, XA_WINDOW, 1, &n, &f, &t);
    if (v && f == 32 && n >= 4) xw->trans = rd32(v);
    free(v);
    xw->fs = xw->maxd = 0;
    v = xw_get_prop(x, xw->id, x->atoms[A_NET_WM_STATE], XA_ATOM, 16, &n, &f, &t);
    if (v && f == 32) for (size_t i = 0; i + 4 <= n; i += 4) {
        uint32_t a = rd32(v + i);
        if (a == x->atoms[A_NET_FS]) xw->fs = 1;
        if (a == x->atoms[A_NET_MAXV] || a == x->atoms[A_NET_MAXH]) xw->maxd = 1;
    }
    free(v);
}

static void xw_read_all(Xwm *x, XWin *xw) {
    xw_read_title(x, xw); xw_read_class(x, xw); xw_read_hints(x, xw); xw_read_protocols(x, xw); xw_read_misc(x, xw);
}

/* PID -> KATE_APP_ID (щоб зіставити вікно з програмою лаунчера) */
static void xw_find_tag(XWin *xw) {
    xw->tag[0] = 0;
    if (xw->pid <= 0) return;
    char path[64]; snprintf(path, sizeof path, "/proc/%d/environ", xw->pid);
    int fd = open(path, O_RDONLY | O_CLOEXEC); if (fd < 0) return;
    static char buf[65536]; ssize_t n = read(fd, buf, sizeof buf - 1); close(fd);
    if (n <= 0) return;
    buf[n] = 0;
    for (ssize_t i = 0; i < n;) {
        size_t len = strlen(buf + i);
        if (!strncmp(buf + i, "KATE_APP_ID=", 12)) { strncpy(xw->tag, buf + i + 12, sizeof xw->tag - 1); return; }
        i += (ssize_t)len + 1;
    }
}

static void xw_set_net_state(Xwm *x, XWin *xw) {
    uint32_t v[4]; int n = 0;
    if (xw->fs) v[n++] = x->atoms[A_NET_FS];
    if (xw->maxd) { v[n++] = x->atoms[A_NET_MAXV]; v[n++] = x->atoms[A_NET_MAXH]; }
    if (n) xw_set_cardinals(x, xw->id, x->atoms[A_NET_WM_STATE], XA_ATOM, v, n);
    else xw_delete_prop(x, xw->id, x->atoms[A_NET_WM_STATE]);
}

static void xw_update_client_list(Xwm *x) {
    uint32_t v[60]; int n = 0;
    for (XWin *w = x->wins; w && n < 60; w = w->next) if (w->managed && w->mapped) v[n++] = w->id;
    xw_set_cardinals(x, x->root, x->atoms[A_NET_CLIENT_LIST], XA_WINDOW, v, n);
}

/* ---------- керування вікном ---------- */
static void xw_manage(Xwm *x, XWin *xw) {
    xw_read_all(x, xw);
    xw_read_misc(x, xw);
    xw_find_tag(xw);
    if (!xw->managed) { xw->managed = 1; xw->slot = xw_free_slot(x); }
    /* підписка на зміни властивостей (заголовок, підказки) */
    { XB q; xb_init(&q, 2, 0); xb_u32(&q, xw->id); xb_u32(&q, 0x800); xb_u32(&q, 0x400000 /* PropertyChange */); xb_send(x, &q); }
    { int32_t bw = 0; xw_configure(x, xw->id, 16, &bw); }   /* без рамки X11: розмір вікна = розмір поверхні */
    xw_activate(x, xw);
    uint32_t st[2] = { 1, 0 };
    xw_set_cardinals(x, xw->id, x->atoms[A_WM_STATE], x->atoms[A_WM_STATE], st, 2);
    uint32_t fe[4] = { 0, 0, 0, 0 };
    xw_set_cardinals(x, xw->id, x->atoms[A_NET_FRAME], XA_CARDINAL, fe, 4);
    xw_map(x, xw->id);
    xw->pend_req = xw->fs ? 1 : 0;
    LOG("xwm: керую вікном 0x%x \"%s\" клас=%s pid=%d tag=%s %dx%d fs=%d", xw->id, xw->title ? xw->title : "", xw->cls ? xw->cls : "", xw->pid, xw->tag, xw->w, xw->h, xw->fs);
}

/* вікно-власник для override-redirect: те з вікон, у чий прямокутник потрапляє точка; інакше активне */
static XWin *xw_owner_of(Xwm *x, XWin *or_) {
    XWin *best = NULL;
    if (or_->trans) { XWin *t = xw_find(x, or_->trans); if (t && t->managed && t->win && t->win->mapped) return t; }
    for (XWin *w = x->wins; w; w = w->next)
        if (w->managed && w->win && w->win->mapped && or_->x >= w->x - 40 && or_->y >= w->y - 40 && or_->x < w->x + w->w + 40 && or_->y < w->y + w->h + 40) { best = w; if (w == x->origin) break; }
    if (best) return best;
    if (x->origin && x->origin->win && x->origin->win->mapped) return x->origin;
    return NULL;
}

/* перед першою появою вікна: тип, батько, заголовок, тег програми */
static void xwm_pre_map(Window *w) {
    Kwl *k = w->k; Xwm *x = k->xwm; XWin *xw = w->xw;
    if (!x || !xw) return;
    free(w->title); w->title = xw->title ? strdup(xw->title) : NULL;
    free(w->app_id); w->app_id = xw->cls ? strdup(xw->cls) : NULL;
    if (xw->tag[0]) strncpy(w->app_tag, xw->tag, sizeof w->app_tag - 1);
    w->minw = xw->minw; w->minh = xw->minh; w->maxw = xw->maxw; w->maxh = xw->maxh;
    w->kind = 0; w->parent = NULL; w->has_deco = 0;
    if (xw->or_ || xw->popup_type) {
        XWin *o = xw_owner_of(x, xw);
        if (o && o->win && o->win != w) { w->kind = 1; w->parent = o->win; w->px = xw->x - o->x; w->py = xw->y - o->y; w->pw = xw->w; w->ph = xw->h; }
    }
    if (w->kind == 0) w->has_deco = xw->nodeco ? 0 : 1;
}

/* вікно щойно з'явилось у лаунчері: виконати відкладені запити (повний екран) */
static void xwm_post_map(Window *w) {
    XWin *xw = w->xw; if (!xw) return;
    xw->mapped = 1;
    if (xw->pend_req && w->kind == 0 && w->k->host.window_request) { xw->pend_req = 0; w->k->host.window_request(w->k->ud, w->id, KWL_REQ_FULLSCREEN, 1); }
    if (w->k->xwm) xw_update_client_list(w->k->xwm);
}

static Window *new_xwindow(Client *c, Surface *s, XWin *xw) {
    Kwl *k = c->k;
    Window *w = calloc(1, sizeof *w);
    w->k = k; w->c = c; w->id = ++k->next_win; w->root = s; w->xw = xw;
    w->kind = 0; w->rfd = -1; w->rscale = 1; w->visible = 1; w->dmg = EMPTY;
    if (xw->tag[0]) strncpy(w->app_tag, xw->tag, sizeof w->app_tag - 1);   /* тег Xwayland не підходить: це служба */
    w->next = k->windows; k->windows = w;
    s->win = w; s->role = 5;
    xw->win = w; xw->surf = s->id;
    return w;
}

/* WL_SURFACE_ID: зв'язати вікно X11 з поверхнею Wayland клієнта-Xwayland */
static void xw_associate(Xwm *x, XWin *xw, uint32_t surf_id) {
    Kwl *k = x->k;
    Client *xc = NULL;
    for (Client *c = k->clients; c; c = c->next) if (!c->dead && c->is_xwl) { xc = c; if (c->pid == x->xpid) break; }
    if (!xc) { LOG("xwm: немає клієнта Xwayland для WL_SURFACE_ID %u", surf_id); return; }
    Obj *o = c_obj(xc, surf_id);
    if (!o || o->iface != I_SURFACE) { xw->pend_surf = surf_id; LOG("xwm: поверхня %u ще не створена — спробую пізніше", surf_id); return; }
    xw->pend_surf = 0;
    Surface *s = o->p;
    if (s->win) { if (s->win->xw == xw) return; }
    if (xw->win) { /* стара поверхня цього вікна */ xw->win->xw = NULL; xw->win = NULL; }
    new_xwindow(xc, s, xw);
    LOG("xwm: вікно X 0x%x <-> поверхня %u (вікно лаунчера %d)%s", xw->id, surf_id, xw->win->id, xw->or_ ? " [override-redirect]" : "");
}

/* ---------- події X11 ---------- */
static void xw_ev_client_message(Xwm *x, const uint8_t *e) {
    uint32_t win = rd32(e + 4), type = rd32(e + 8); const uint8_t *d = e + 12;
    XWin *xw = xw_find(x, win);
    if (type == x->atoms[A_WL_SURFACE_ID]) {
        if (!xw) xw = xw_get(x, win);
        xw_associate(x, xw, rd32(d)); return;
    }
    if (!xw) return;
    Kwl *k = x->k;
    if (type == x->atoms[A_NET_WM_STATE]) {
        uint32_t act = rd32(d), a1 = rd32(d + 4), a2 = rd32(d + 8);
        int fs = -1, mx = -1;
        for (int i = 0; i < 2; i++) {
            uint32_t a = i ? a2 : a1; if (!a) continue;
            int cur = 0, *tgt = NULL;
            if (a == x->atoms[A_NET_FS]) { cur = xw->fs; tgt = &fs; }
            else if (a == x->atoms[A_NET_MAXV] || a == x->atoms[A_NET_MAXH]) { cur = xw->maxd; tgt = &mx; }
            if (!tgt) continue;
            *tgt = act == 0 ? 0 : act == 1 ? 1 : !cur;
        }
        if (xw->win && xw->win->mapped && k->host.window_request) {
            if (fs >= 0 && fs != xw->fs) k->host.window_request(k->ud, xw->win->id, KWL_REQ_FULLSCREEN, fs);
            if (mx >= 0 && mx != xw->maxd) k->host.window_request(k->ud, xw->win->id, KWL_REQ_MAXIMIZE, mx);
        } else {
            if (fs >= 0) xw->fs = fs; if (mx >= 0) xw->maxd = mx;
            xw_set_net_state(x, xw);
            if (fs == 1) xw->pend_req = 1;
        }
    } else if (type == x->atoms[A_WM_CHANGE_STATE]) {
        if (rd32(d) == 3 && xw->win && k->host.window_request) k->host.window_request(k->ud, xw->win->id, KWL_REQ_MINIMIZE, 1);
    } else if (type == x->atoms[A_NET_MOVERESIZE]) {
        int dir = (int)rd32(d + 8);
        if (xw->win && k->host.window_request) {
            static const int edge[8] = { 5, 1, 9, 8, 10, 2, 6, 4 };   /* TL,T,TR,R,BR,B,BL,L -> xdg */
            if (dir == 8) k->host.window_request(k->ud, xw->win->id, KWL_REQ_MOVE, 0);
            else if (dir >= 0 && dir < 8) k->host.window_request(k->ud, xw->win->id, KWL_REQ_RESIZE, edge[dir]);
        }
    } else if (type == x->atoms[A_NET_REQ_FRAME]) {
        uint32_t fe[4] = { 0, 0, 0, 0 };
        xw_set_cardinals(x, xw->id, x->atoms[A_NET_FRAME], XA_CARDINAL, fe, 4);
    }
}

static void xw_ev(Xwm *x, const uint8_t *e) {
    Kwl *k = x->k;
    switch (e[0] & 0x7f) {
    case 16: { /* CreateNotify */
        if (rd32(e + 4) != x->root) break;
        XWin *xw = xw_get(x, rd32(e + 8));
        xw->x = (int16_t)rd16(e + 12); xw->y = (int16_t)rd16(e + 14); xw->w = rd16(e + 16); xw->h = rd16(e + 18); xw->or_ = e[22];
        break; }
    case 17: { /* DestroyNotify */
        XWin *xw = xw_find(x, rd32(e + 8)); if (xw) { int was = xw->managed; xw_free(x, xw); if (was) xw_update_client_list(x); }
        break; }
    case 18: { /* UnmapNotify */
        XWin *xw = xw_find(x, rd32(e + 8)); if (!xw) break;
        xw->mapped = 0;
        if (xw == x->origin) x->origin = NULL;
        if (xw->managed) { uint32_t st[2] = { 0, 0 }; xw_set_cardinals(x, xw->id, x->atoms[A_WM_STATE], x->atoms[A_WM_STATE], st, 2); xw_update_client_list(x); }
        break; }
    case 19: { /* MapNotify */
        XWin *xw = xw_get(x, rd32(e + 8));
        if (e[12]) { xw->or_ = 1; }
        break; }
    case 20: { /* MapRequest */
        XWin *xw = xw_get(x, rd32(e + 8));
        xw_manage(x, xw);
        break; }
    case 22: { /* ConfigureNotify */
        XWin *xw = xw_find(x, rd32(e + 8)); if (!xw) break;
        int nx = (int16_t)rd16(e + 16), ny = (int16_t)rd16(e + 18), nw = rd16(e + 20), nh = rd16(e + 22);
        int moved = nx != xw->x || ny != xw->y || nw != xw->w || nh != xw->h;
        xw->x = nx; xw->y = ny; xw->w = nw; xw->h = nh;
        if (e[26]) xw->or_ = 1;
        if (moved && xw->win && xw->win->mapped && xw->win->kind == 1 && xw->win->parent && xw->win->parent->xw) {
            Window *w = xw->win; XWin *o = w->parent->xw;
            w->px = xw->x - o->x; w->py = xw->y - o->y; w->pw = xw->w; w->ph = xw->h;
            if (k->host.window_popup) k->host.window_popup(k->ud, w->id, w->px, w->py, w->pw, w->ph);
        }
        break; }
    case 23: { /* ConfigureRequest */
        uint32_t win = rd32(e + 8); uint16_t m = rd16(e + 26);
        XWin *xw = xw_get(x, win);
        int nw = (m & 4) ? rd16(e + 20) : xw->w, nh = (m & 8) ? rd16(e + 22) : xw->h;
        int nx = xw->x, ny = xw->y;
        if (!xw->managed) { if (m & 1) nx = (int16_t)rd16(e + 16); if (m & 2) ny = (int16_t)rd16(e + 18); }
        if (nw < 1) nw = 1;
        if (nh < 1) nh = 1;
        int changed = nw != xw->w || nh != xw->h || nx != xw->x || ny != xw->y;
        xw_move_resize(x, xw, nx, ny, nw, nh);
        if (!changed) xw_synth_configure(x, xw);
        break; }
    case 28: { /* PropertyNotify */
        XWin *xw = xw_find(x, rd32(e + 4)); if (!xw || !xw->managed) break;
        uint32_t a = rd32(e + 8);
        if (a == x->atoms[A_NET_WM_NAME] || a == XA_WM_NAME) {
            xw_read_title(x, xw);
            if (xw->win && xw->win->mapped && k->host.window_title) { free(xw->win->title); xw->win->title = xw->title ? strdup(xw->title) : NULL; k->host.window_title(k->ud, xw->win->id, xw->title ? xw->title : ""); }
        } else if (a == XA_WM_CLASS) {
            xw_read_class(x, xw);
            if (xw->win && xw->win->mapped && k->host.window_appid) k->host.window_appid(k->ud, xw->win->id, xw->cls ? xw->cls : "");
        } else if (a == XA_WM_NORMAL_HINTS) {
            xw_read_hints(x, xw);
            if (xw->win && xw->win->mapped && k->host.window_hints) k->host.window_hints(k->ud, xw->win->id, xw->minw, xw->minh, xw->maxw, xw->maxh);
        } else if (a == x->atoms[A_WM_PROTOCOLS]) xw_read_protocols(x, xw);
        else if (a == XA_WM_HINTS) xw_read_hints(x, xw);
        break; }
    case 33: xw_ev_client_message(x, e); break;
    default: break;
    }
}

static void xw_drain(Xwm *x) {
    for (;;) {
        if (x->nevq) { uint8_t e[32]; memcpy(e, x->evq[0], 32); memmove(x->evq, x->evq + 1, (size_t)(x->nevq - 1) * 32); x->nevq--; xw_ev(x, e); continue; }
        size_t n = xw_pktlen(x); if (!n) break;
        uint8_t t = x->rb[0], e[32]; memcpy(e, x->rb, 32);
        if (t == 0) LOG("xwm: помилка X11 code=%u (op=%u, id=0x%x)", e[1], e[10], rd32(e + 4));
        xw_consume(x, n);
        if (t >= 2) xw_ev(x, e);
    }
}

/* поверхня Wayland могла з'явитись пізніше за повідомлення WL_SURFACE_ID (різні сокети) */
static void xwm_retry(Kwl *k) {
    Xwm *x = k->xwm; if (!x) return;
    for (XWin *w = x->wins; w; w = w->next) if (w->pend_surf) xw_associate(x, w, w->pend_surf);
}

/* викликається, коли сокет X читабельний */
static void xwm_read(Kwl *k) {
    Xwm *x = k->xwm; if (!x) return;
    for (;;) { int r = xw_rd(x, -1); if (r < 0) { x->broken = 1; break; } if (r == 0) break; }
    xw_drain(x);
    if (x->broken) xwm_close_conn(k);
}

/* ---------- публічні дії, які викликає ядро ---------- */
static void xwm_focus(Kwl *k, Window *w) {
    Xwm *x = k->xwm; if (!x || x->broken) return;
    if (w && w->xw && w->xw->managed) {
        XWin *xw = w->xw;
        xw_activate(x, xw);
        { XB q; xb_init(&q, 42, 2); xb_u32(&q, xw->id); xb_u32(&q, 0); if (xw->input || !xw->take_focus) xb_send(x, &q); }
        if (xw->take_focus) xw_client_msg(x, xw->id, xw->id, x->atoms[A_WM_PROTOCOLS], x->atoms[A_WM_TAKE_FOCUS], 0, 0);
        uint32_t v = xw->id; xw_set_cardinals(x, x->root, x->atoms[A_NET_ACTIVE], XA_WINDOW, &v, 1);
        xw_sync(x);   /* X-сервер вже виконав переміщення — лише тоді шлемо події Wayland */
        xw_drain(x);
    } else if (w == NULL || !w->xw) {
        XB q; xb_init(&q, 42, 0); xb_u32(&q, 0); xb_u32(&q, 0); xb_send(x, &q);
    }
}

static void xwm_close(Window *w) {
    Xwm *x = w->k->xwm; XWin *xw = w->xw; if (!x || !xw) return;
    if (xw->has_delete) xw_client_msg(x, xw->id, xw->id, x->atoms[A_WM_PROTOCOLS], x->atoms[A_WM_DELETE], 0, 0);
    else { XB q; xb_init(&q, 113, 0); xb_u32(&q, xw->id); xb_send(x, &q); }
}

static void xwm_configure(Window *w, int cw, int ch, int flags) {
    Xwm *x = w->k->xwm; XWin *xw = w->xw; if (!x || !xw || !xw->managed) return;
    int fs = (flags >> 1) & 1, mx = flags & 1;
    int ch_state = fs != xw->fs || mx != xw->maxd;
    xw->fs = fs; xw->maxd = mx;
    if (ch_state) xw_set_net_state(x, xw);
    if (cw > 0 && ch > 0 && (cw != xw->w || ch != xw->h)) xw_move_resize(x, xw, xw->x, xw->y, cw, ch);
    else xw_synth_configure(x, xw);
}

static void xwm_window_gone(Window *w) {
    if (w->xw) { if (w->xw->win == w) w->xw->win = NULL; w->xw = NULL; }
}

/* ---------- підключення ---------- */
static void xwm_marker(Kwl *k, int on) {
    char p[352]; snprintf(p, sizeof p, "%s/xwm.ready", k->dir);
    if (on) { FILE *f = fopen(p, "w"); if (f) { fprintf(f, "1\n"); fclose(f); } } else unlink(p);
}

static void xwm_close_conn(Kwl *k) {
    Xwm *x = k->xwm; if (!x) return;
    LOG("xwm: з'єднання закрито");
    while (x->wins) xw_free(x, x->wins);
    if (x->fd >= 0) close(x->fd);
    free(x->rb); free(x->evq); free(x);
    k->xwm = NULL;
    xwm_marker(k, 0);
}

static int xwm_connect(Kwl *k) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0); if (fd < 0) return -1;
    struct sockaddr_un a; memset(&a, 0, sizeof a); a.sun_family = AF_UNIX;
    if (strlen(k->xwm_path) >= sizeof a.sun_path) { close(fd); return -1; }
    strcpy(a.sun_path, k->xwm_path);
    if (connect(fd, (struct sockaddr *)&a, sizeof a) < 0) { close(fd); return -1; }
    Xwm *x = calloc(1, sizeof *x); x->k = k; x->fd = fd;
    /* PID сервера (для зіставлення з клієнтом Wayland) */
    struct ucred cr; socklen_t cl = sizeof cr;
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cr, &cl) == 0) x->xpid = cr.pid;
    uint8_t su[12]; memset(su, 0, sizeof su); su[0] = 'l'; su[2] = 11;
    if (xw_write_all(x, su, sizeof su) < 0) { close(fd); free(x); return -1; }
    /* відповідь на рукостискання */
    uint32_t t0 = now_ms();
    while (x->rl < 8) { int r = xw_rd(x, 2000 - (int)(now_ms() - t0)); if (r <= 0) { close(fd); free(x->rb); free(x); return -1; } }
    size_t total = 8 + (size_t)rd16(x->rb + 6) * 4;
    while (x->rl < total) { int r = xw_rd(x, 2000 - (int)(now_ms() - t0)); if (r <= 0) { close(fd); free(x->rb); free(x); return -1; } }
    if (x->rb[0] != 1) { LOG("xwm: X-сервер відхилив підключення"); close(fd); free(x->rb); free(x); return -1; }
    const uint8_t *s = x->rb;
    x->base = rd32(s + 12); x->mask = rd32(s + 16);
    x->shift = (uint32_t)__builtin_ctz(x->mask ? x->mask : 1);
    uint32_t vlen = rd16(s + 24); int nfmt = s[29];
    size_t off = 40 + ((vlen + 3) & ~3u) + (size_t)nfmt * 8;
    if (off + 12 > total) { close(fd); free(x->rb); free(x); return -1; }
    x->root = rd32(s + off);
    xw_consume(x, total);
    k->xwm = x;

    /* атоми (конвеєром) */
    uint16_t seqs[A_COUNT];
    for (int i = 0; i < A_COUNT; i++) {
        XB q; xb_init(&q, 16, 0); xb_u16(&q, (uint32_t)strlen(XANAMES[i])); xb_u16(&q, 0); xb_bytes(&q, XANAMES[i], (int)strlen(XANAMES[i]));
        seqs[i] = xb_send(x, &q);
    }
    for (int i = 0; i < A_COUNT; i++) { uint8_t *r = xw_wait(x, seqs[i], NULL); x->atoms[i] = r ? rd32(r + 8) : 0; free(r); }
    if (x->broken) { xwm_close_conn(k); return -1; }

    /* Composite */
    { XB q; xb_init(&q, 98, 0); const char *nm = "Composite"; xb_u16(&q, (uint32_t)strlen(nm)); xb_u16(&q, 0); xb_bytes(&q, nm, (int)strlen(nm));
      uint8_t *r = xw_wait(x, xb_send(x, &q), NULL);
      if (r && r[8]) x->composite_op = r[9];
      free(r); }
    if (!x->composite_op) { LOG("xwm: на X-сервері немає розширення Composite"); xwm_close_conn(k); return -1; }
    { XB q; xb_init(&q, x->composite_op, 0); xb_u32(&q, 0); xb_u32(&q, 4); free(xw_wait(x, xb_send(x, &q), NULL)); }   /* QueryVersion */

    /* стаємо менеджером вікон */
    { XB q; xb_init(&q, 2, 0); xb_u32(&q, x->root); xb_u32(&q, 0x800); xb_u32(&q, 0x180000 | 0x400000); /* SubstructureRedirect|Notify|PropertyChange */ xb_send(x, &q); }
    { XB q; xb_init(&q, x->composite_op, 2); xb_u32(&q, x->root); xb_u8(&q, 1); xb_u8(&q, 0); xb_u16(&q, 0); xb_send(x, &q); }  /* RedirectSubwindows, Manual */

    /* EWMH: вікно-свідок */
    x->check_win = x->base | (1u << x->shift);
    { XB q; xb_init(&q, 1, 0); xb_u32(&q, x->check_win); xb_u32(&q, x->root); xb_u16(&q, 0); xb_u16(&q, 0); xb_u16(&q, 1); xb_u16(&q, 1); xb_u16(&q, 0); xb_u16(&q, 2 /* InputOnly */); xb_u32(&q, 0); xb_u32(&q, 0); /* CopyFromParent */
      q.b[1] = 0; xb_send(x, &q); }
    xw_set_cardinals(x, x->root, x->atoms[A_NET_CHECK], XA_WINDOW, &x->check_win, 1);
    xw_set_cardinals(x, x->check_win, x->atoms[A_NET_CHECK], XA_WINDOW, &x->check_win, 1);
    xw_change_prop(x, x->check_win, x->atoms[A_NET_WM_NAME], x->atoms[A_UTF8], 8, "KateLauncher", 12);
    uint32_t sup[] = { x->atoms[A_NET_SUPPORTED], x->atoms[A_NET_CHECK], x->atoms[A_NET_WM_NAME], x->atoms[A_NET_WM_STATE], x->atoms[A_NET_FS],
        x->atoms[A_NET_MAXV], x->atoms[A_NET_MAXH], x->atoms[A_NET_ACTIVE], x->atoms[A_NET_CLOSE], x->atoms[A_NET_MOVERESIZE],
        x->atoms[A_NET_CLIENT_LIST], x->atoms[A_NET_FRAME], x->atoms[A_NET_WTYPE], x->atoms[A_NET_WM_PID], x->atoms[A_NET_REQ_FRAME] };
    xw_set_cardinals(x, x->root, x->atoms[A_NET_SUPPORTED], XA_ATOM, sup, (int)(sizeof sup / sizeof sup[0]));
    uint32_t one = 1, zero = 0; (void)zero;
    xw_set_cardinals(x, x->root, x->atoms[A_NET_NDESK], XA_CARDINAL, &one, 1);
    xw_set_cardinals(x, x->root, x->atoms[A_NET_CURDESK], XA_CARDINAL, &zero, 1);
    xw_sync(x);
    if (x->broken) { xwm_close_conn(k); return -1; }
    x->ready = 1;
    xwm_marker(k, 1);
    LOG("xwm: готовий (root=0x%x, pid X-сервера %d)", x->root, x->xpid);
    return 0;
}
