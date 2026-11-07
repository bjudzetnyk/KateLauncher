/* KateLauncher Wayland compositor core. Див. kwl.h */
#define _GNU_SOURCE
#include "kwl.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <time.h>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/eventfd.h>
#include <sys/syscall.h>

#define MAXMSG 4096
#define MAX_OBJ (1u << 20)
#define SID_BASE 0xff000000u

static int g_debug;
#define LOG(...) do { if (g_debug) { fprintf(stderr, "kwl: " __VA_ARGS__); fputc('\n', stderr); } } while (0)

/* ---------- Типи ---------- */
typedef struct Client Client;
typedef struct Surface Surface;
typedef struct Window Window;
typedef struct Pool Pool;
typedef struct Buffer Buffer;
typedef struct DSrc DSrc;
typedef struct DOffer DOffer;
typedef struct Reader Reader;
typedef struct XWin XWin;
typedef struct Xwm Xwm;

enum { I_NONE = 0, I_DISPLAY, I_REGISTRY, I_CALLBACK, I_COMPOSITOR, I_SUBCOMP, I_SHM, I_POOL, I_BUFFER,
       I_SURFACE, I_REGION, I_SUBSURFACE, I_OUTPUT, I_SEAT, I_POINTER, I_KEYBOARD, I_DDM, I_DSRC, I_DDEV,
       I_DOFFER, I_WMBASE, I_POSITIONER, I_XSURF, I_TOPLEVEL, I_POPUP, I_DECOMGR, I_DECO };

enum { G_COMPOSITOR = 1, G_SUBCOMP, G_SHM, G_OUTPUT, G_SEAT, G_DDM, G_WMBASE, G_DECOMGR };

typedef struct { uint8_t iface; uint8_t version; void *p; } Obj;
typedef struct { int x0, y0, x1, y1; } Rect;   /* x1,y1 — виключно; порожній: x1<=x0 */

struct Pool { int fd; uint8_t *data; size_t size; int refs; };
struct Buffer { Pool *pool; int off, w, h, stride; uint32_t fmt; Client *c; uint32_t id; int refs; int dead; };

typedef struct { int x, y, w, h; int anchor, gravity; int ox, oy; int ax, ay, aw, ah; } Positioner;

struct Surface {
    Client *c; uint32_t id;
    Buffer *pend; int has_attach;         /* attach у черзі */
    Buffer *cur;                          /* поточний (утримується до заміни) */
    int pend_scale, scale;
    Rect pdmg; int pdmg_full; int pdmg_given;
    uint32_t *pframes; int npframes;      /* відкладені wl_callback */
    int role;                             /* 0 none, 1 toplevel, 2 popup, 3 sub, 4 cursor */
    Window *win;                          /* для кореня вікна */
    Surface *parent;                      /* для субповерхні */
    int sub_x, sub_y;                     /* у логічних px батька */
    Surface **kids; int nkids;            /* знизу вгору */
    void *xs;                             /* XSurf */
    uint32_t sub_id;
    int entered;
};

typedef struct { Client *c; uint32_t id; Surface *s; Window *w; int gx, gy, gw, gh; int has_geom; int cfg_sent; } XSurf;

struct Window {
    Kwl *k; Client *c; int id; Surface *root; XSurf *xs;
    uint32_t top_id, xs_id, deco_id;
    int kind;                             /* 0 toplevel, 1 popup */
    Window *parent; int px, py, pw, ph;
    char *title, *app_id; char app_tag[64];
    int mapped, visible, stale, has_deco, grab;
    int w, h;                             /* логічний розмір кореня */
    int minw, minh, maxw, maxh;
    int maximized, fullscreen, resizing;
    int cfg_w, cfg_h;   /* останній розмір, який ми просили (для повторних configure при зміні фокуса) */
    int gen; int rfd; uint32_t *region; size_t rsize; int rw, rh, rscale;
    Rect dmg;
    uint32_t *frames; int nframes;
    XWin *xw;                             /* вікно X11 (Xwayland) — вікно без xdg_surface */
    Window *next;
};

struct DSrc { Client *c; uint32_t id; char *mimes[16]; int nm; int dead; };
struct DOffer { Client *c; uint32_t id; DSrc *src; int android; };
struct Reader { int fd; char *buf; size_t len, cap; uint32_t deadline; int gen; Reader *next; };

struct Client {
    Kwl *k; int fd; Client *next; int dead;
    uint8_t *ib; size_t ilen, icap;
    uint8_t *ob; size_t olen, ocap, osent; size_t mstart;
    int ofds[8]; size_t ofdend[8]; int nofd;
    int ifds[32]; int nifd;
    Obj *objs; uint32_t nobjs;
    Obj *sobjs; uint32_t nsobjs;
    int pid; char app_tag[64];
    uint32_t outputs[4]; int nout;
    uint32_t pointers[8]; int npointers;
    uint32_t keyboards[8]; int nkeyboards;
    uint32_t ddevs[8]; int nddevs;
    int seat_ver;
    int is_xwl;                           /* клієнт — Xwayland */
};

typedef struct { int op, a, b, c, d; double x, y; char *s; } Cmd;

struct Kwl {
    KwlHost host; void *ud;
    char dir[256], sockpath[320], pidpath[320];
    int lfd, efd;
    Client *clients; Window *windows; int next_win;
    char *keymap; size_t keymap_len;
    int out_w, out_h, scale, fps;
    uint32_t serial;
    uint32_t last_tick, tick_due; int work;
    Cmd *q; int qn, qcap; pthread_mutex_t qm;
    int stop;
    /* вхід */
    Window *kfocus; Surface *pfocus; double px, py;
    uint32_t mods_dep, mods_lock; int group; unsigned char down[256];
    /* буфер обміну */
    DSrc *sel_src; char *android_clip; int sel_gen; Reader *readers;
    /* життєвий цикл */
    int had_window, nmapped; uint32_t idle_since, start_ms, grace_until;
    int idle_ms;                          /* простій без вікон до виходу; 0 — ніколи */
    int dbg;
    /* X11 */
    Xwm *xwm; char xwm_path[320]; uint32_t xwm_next_try, xwm_deadline;
};

static uint32_t now_ms(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000u + ts.tv_nsec / 1000000u);
}
static uint32_t next_serial(Kwl *k) { return ++k->serial; }
static int32_t fx(double v) { return (int32_t)(v * 256.0); }

/* ---------- Прямокутники ---------- */
static int rect_empty(const Rect *r) { return r->x1 <= r->x0 || r->y1 <= r->y0; }
static void rect_union(Rect *a, const Rect *b) {
    if (rect_empty(b)) return;
    if (rect_empty(a)) { *a = *b; return; }
    if (b->x0 < a->x0) a->x0 = b->x0;
    if (b->y0 < a->y0) a->y0 = b->y0;
    if (b->x1 > a->x1) a->x1 = b->x1;
    if (b->y1 > a->y1) a->y1 = b->y1;
}
static Rect rect_isect(Rect a, Rect b) {
    Rect r = { a.x0 > b.x0 ? a.x0 : b.x0, a.y0 > b.y0 ? a.y0 : b.y0, a.x1 < b.x1 ? a.x1 : b.x1, a.y1 < b.y1 ? a.y1 : b.y1 };
    if (r.x1 < r.x0) r.x1 = r.x0;
    if (r.y1 < r.y0) r.y1 = r.y0;
    return r;
}
static const Rect EMPTY = { 0, 0, 0, 0 };

/* ---------- Таблиця об'єктів ---------- */
static Obj *c_obj(Client *c, uint32_t id) {
    if (id >= SID_BASE) { uint32_t i = id - SID_BASE; return i < c->nsobjs && c->sobjs[i].iface ? &c->sobjs[i] : NULL; }
    return id < c->nobjs && c->objs[id].iface ? &c->objs[id] : NULL;
}
static Obj *c_new(Client *c, uint32_t id, int iface, int ver, void *p) {
    Obj **tab; uint32_t *n; uint32_t idx = id;
    if (id >= SID_BASE) { tab = &c->sobjs; n = &c->nsobjs; idx = id - SID_BASE; }
    else { tab = &c->objs; n = &c->nobjs; }
    if (idx >= MAX_OBJ) return NULL;
    if (idx >= *n) {
        uint32_t nn = *n ? *n : 64;
        while (nn <= idx) nn *= 2;
        Obj *t = realloc(*tab, nn * sizeof(Obj));
        if (!t) return NULL;
        memset(t + *n, 0, (nn - *n) * sizeof(Obj));
        *tab = t; *n = nn;
    }
    Obj *o = &(*tab)[idx];
    o->iface = (uint8_t)iface; o->version = (uint8_t)ver; o->p = p;
    return o;
}
static void c_free_obj(Client *c, uint32_t id) { Obj *o = c_obj(c, id); if (o) { o->iface = 0; o->p = NULL; } }

/* ---------- Вихідні повідомлення ---------- */
static int ob_reserve(Client *c, size_t n) {
    if (c->olen + n > c->ocap) {
        size_t nc = c->ocap ? c->ocap : 4096;
        while (nc < c->olen + n) nc *= 2;
        if (nc > (8u << 20)) return 0;
        uint8_t *t = realloc(c->ob, nc);
        if (!t) return 0;
        c->ob = t; c->ocap = nc;
    }
    return 1;
}
static void msg_start(Client *c, uint32_t obj, uint32_t op) {
    if (!ob_reserve(c, 8)) { c->dead = 1; return; }
    c->mstart = c->olen;
    uint32_t h[2] = { obj, op };
    memcpy(c->ob + c->olen, h, 8); c->olen += 8;
}
static void put_u32(Client *c, uint32_t v) { if (!ob_reserve(c, 4)) { c->dead = 1; return; } memcpy(c->ob + c->olen, &v, 4); c->olen += 4; }
static void put_str(Client *c, const char *s) {
    uint32_t n = s ? (uint32_t)strlen(s) + 1 : 0;
    put_u32(c, n);
    if (!n) return;
    size_t pad = (n + 3) & ~3u;
    if (!ob_reserve(c, pad)) { c->dead = 1; return; }
    memset(c->ob + c->olen, 0, pad); memcpy(c->ob + c->olen, s, n); c->olen += pad;
}
static void put_arr(Client *c, const void *d, uint32_t n) {
    put_u32(c, n);
    size_t pad = (n + 3) & ~3u;
    if (!ob_reserve(c, pad)) { c->dead = 1; return; }
    memset(c->ob + c->olen, 0, pad); if (n) memcpy(c->ob + c->olen, d, n); c->olen += pad;
}
static void msg_end(Client *c, int fd) {
    uint32_t sz = (uint32_t)(c->olen - c->mstart);
    uint32_t w1; memcpy(&w1, c->ob + c->mstart + 4, 4);
    w1 |= sz << 16;
    memcpy(c->ob + c->mstart + 4, &w1, 4);
    if (fd >= 0) {
        if (c->nofd < 8) { c->ofds[c->nofd] = fd; c->ofdend[c->nofd] = c->olen; c->nofd++; }
        else close(fd);
    }
}
#define EV0(c, obj, op) do { msg_start(c, obj, op); msg_end(c, -1); } while (0)

static int client_flush(Client *c) {
    while (c->osent < c->olen) {
        size_t end = c->olen; int fd = -1;
        if (c->nofd) { end = c->ofdend[0]; fd = c->ofds[0]; }
        struct iovec iov = { c->ob + c->osent, end - c->osent };
        struct msghdr mh; memset(&mh, 0, sizeof mh);
        mh.msg_iov = &iov; mh.msg_iovlen = 1;
        char cb[CMSG_SPACE(sizeof(int))];
        if (fd >= 0) {
            mh.msg_control = cb; mh.msg_controllen = sizeof cb;
            struct cmsghdr *cm = CMSG_FIRSTHDR(&mh);
            cm->cmsg_level = SOL_SOCKET; cm->cmsg_type = SCM_RIGHTS; cm->cmsg_len = CMSG_LEN(sizeof(int));
            memcpy(CMSG_DATA(cm), &fd, sizeof(int));
        }
        ssize_t n = sendmsg(c->fd, &mh, MSG_NOSIGNAL | MSG_DONTWAIT);
        if (n < 0) { if (errno == EAGAIN || errno == EINTR) return 0; c->dead = 1; return -1; }
        c->osent += (size_t)n;
        if (fd >= 0 && c->osent >= end) {
            close(fd); memmove(c->ofds, c->ofds + 1, (c->nofd - 1) * sizeof(int));
            memmove(c->ofdend, c->ofdend + 1, (c->nofd - 1) * sizeof(size_t)); c->nofd--;
        }
    }
    c->olen = c->osent = 0;
    return 0;
}

/* ---------- Читання аргументів ---------- */
typedef struct { const uint8_t *p, *end; Client *c; int err; } Rd;
static uint32_t rd_u32(Rd *r) { if (r->p + 4 > r->end) { r->err = 1; return 0; } uint32_t v; memcpy(&v, r->p, 4); r->p += 4; return v; }
static int32_t rd_i32(Rd *r) { return (int32_t)rd_u32(r); }
static const char *rd_str(Rd *r) {
    uint32_t n = rd_u32(r);
    if (r->err || !n) return NULL;
    size_t pad = (n + 3) & ~3u;
    if (r->p + pad > r->end) { r->err = 1; return NULL; }
    const char *s = (const char *)r->p; r->p += pad;
    if (s[n - 1] != 0) { r->err = 1; return NULL; }
    return s;
}
static void rd_arr(Rd *r) { uint32_t n = rd_u32(r); size_t pad = (n + 3) & ~3u; if (r->p + pad > r->end) { r->err = 1; return; } r->p += pad; }
static int rd_fd(Rd *r) {
    Client *c = r->c;
    if (!c->nifd) { r->err = 1; return -1; }
    int fd = c->ifds[0]; memmove(c->ifds, c->ifds + 1, (--c->nifd) * sizeof(int)); return fd;
}

/* ---------- Спільна пам'ять ---------- */
static int mkshm(size_t size) {
    int fd = (int)syscall(SYS_memfd_create, "kwl", 1u /*MFD_CLOEXEC*/);
    if (fd < 0) {
        char t[] = "/tmp/kwl-XXXXXX";
        fd = mkostemp(t, O_CLOEXEC);
        if (fd >= 0) unlink(t);
    }
    if (fd < 0) return -1;
    if (ftruncate(fd, (off_t)size) < 0) { close(fd); return -1; }
    return fd;
}

static void send_delete_id(Client *c, uint32_t id) { msg_start(c, 1, 1); put_u32(c, id); msg_end(c, -1); }
static void obj_gone(Client *c, uint32_t id) { c_free_obj(c, id); if (id < SID_BASE) send_delete_id(c, id); }

/* ---------- Вікна, область пікселів, композиція ---------- */
static void win_send_configure(Window *w, int cw, int ch);
static void surface_abs(Surface *s, int *x, int *y);
static void xwm_pre_map(Window *w);
static void xwm_post_map(Window *w);
static void xwm_focus(Kwl *k, Window *w);
static void xwm_close(Window *w);
static void xwm_configure(Window *w, int cw, int ch, int flags);
static void xwm_window_gone(Window *w);
static void xwm_read(Kwl *k);
static void xwm_retry(Kwl *k);
static int xwm_connect(Kwl *k);
static void xwm_close_conn(Kwl *k);

static Window *find_window(Kwl *k, int id) {
    for (Window *w = k->windows; w; w = w->next) if (w->id == id) return w;
    return NULL;
}

static Surface *root_of(Surface *s) { while (s && s->parent) s = s->parent; return s; }
static Window *window_of(Surface *s) { Surface *r = root_of(s); return r ? r->win : NULL; }

static void buf_put(Buffer *b);
static void buf_free(Buffer *b) {
    if (b->pool && --b->pool->refs <= 0) { if (b->pool->data) munmap(b->pool->data, b->pool->size); if (b->pool->fd >= 0) close(b->pool->fd); free(b->pool); }
    free(b);
}
static void buf_put(Buffer *b) {
    if (!b) return;
    if (--b->refs > 0) return;
    b->refs = 0;
    if (b->dead) { buf_free(b); return; }
    Client *c = b->c;
    if (c && !c->dead) EV0(c, b->id, 0);   /* wl_buffer.release */
}

static void region_free(Window *w) {
    if (w->region) munmap(w->region, w->rsize);
    if (w->rfd >= 0) close(w->rfd);
    w->region = NULL; w->rfd = -1; w->rsize = 0; w->rw = w->rh = 0;
}

static int region_alloc(Window *w, int bw, int bh, int scale) {
    if (bw <= 0 || bh <= 0 || bw > 16384 || bh > 16384) return 0;
    size_t size = (size_t)bw * bh * 4;
    int fd = mkshm(size);
    if (fd < 0) return 0;
    void *m = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (m == MAP_FAILED) { close(fd); return 0; }
    region_free(w);
    w->region = m; w->rfd = fd; w->rsize = size; w->rw = bw; w->rh = bh; w->rscale = scale; w->gen++;
    return 1;
}

static void send_buffer(Window *w) {
    if (w->k->host.window_buffer)
        w->k->host.window_buffer(w->k->ud, w->id, w->gen, w->rw, w->rh, w->rscale, w->rfd, (int)w->rsize);
}

static inline uint32_t swz(uint32_t p) { return (p & 0xFF00FF00u) | ((p >> 16) & 0xFFu) | ((p & 0xFFu) << 16); }

static void blit_buf(Window *w, Buffer *b, int ox, int oy, Rect clip, int blend) {
    if (!w->region || !b || !b->pool || !b->pool->data) return;
    Rect br = { ox, oy, ox + b->w, oy + b->h };
    Rect rr = { 0, 0, w->rw, w->rh };
    Rect r = rect_isect(rect_isect(clip, br), rr);
    if (rect_empty(&r)) return;
    const uint8_t *base = b->pool->data + b->off;
    int xrgb = b->fmt == 1;
    int n = r.x1 - r.x0;
    for (int y = r.y0; y < r.y1; y++) {
        const uint32_t *s = (const uint32_t *)(base + (size_t)(y - oy) * b->stride) + (r.x0 - ox);
        uint32_t *d = w->region + (size_t)y * w->rw + r.x0;
        if (!blend || xrgb) {
            if (xrgb) for (int i = 0; i < n; i++) d[i] = swz(s[i]) | 0xFF000000u;
            else for (int i = 0; i < n; i++) d[i] = swz(s[i]);
        } else {
            for (int i = 0; i < n; i++) {
                uint32_t p = swz(s[i]); uint32_t a = p >> 24;
                if (a == 255) d[i] = p;
                else if (a) {
                    uint32_t dd = d[i], inv = 255 - a;
                    uint32_t rr_ = ((dd & 0xFF) * inv + 127) / 255 + (p & 0xFF);
                    uint32_t gg = (((dd >> 8) & 0xFF) * inv + 127) / 255 + ((p >> 8) & 0xFF);
                    uint32_t bb = (((dd >> 16) & 0xFF) * inv + 127) / 255 + ((p >> 16) & 0xFF);
                    uint32_t aa = (((dd >> 24) & 0xFF) * inv + 127) / 255 + a;
                    if (rr_ > 255) rr_ = 255; if (gg > 255) gg = 255; if (bb > 255) bb = 255; if (aa > 255) aa = 255;
                    d[i] = rr_ | (gg << 8) | (bb << 16) | (aa << 24);
                }
            }
        }
    }
}

static void redraw_tree(Window *w, Surface *s, int ox, int oy, Rect clip, int root) {
    if (s->cur) blit_buf(w, s->cur, ox, oy, clip, !root);
    for (int i = 0; i < s->nkids; i++) {
        Surface *ch = s->kids[i];
        if (!ch->cur) continue;
        redraw_tree(w, ch, ox + ch->sub_x * w->rscale, oy + ch->sub_y * w->rscale, clip, 0);
    }
}

static void surface_abs(Surface *s, int *x, int *y) {
    int ax = 0, ay = 0;
    Window *w = window_of(s);
    int sc = w && w->rscale > 0 ? w->rscale : 1;
    for (; s && s->parent; s = s->parent) { ax += s->sub_x * sc; ay += s->sub_y * sc; }
    *x = ax; *y = ay;
}

static void window_damage_rect(Window *w, Rect d) {
    Rect full = { 0, 0, w->rw, w->rh };
    d = rect_isect(d, full);
    if (rect_empty(&d)) return;
    if (w->visible) {
        redraw_tree(w, w->root, 0, 0, d, 1);
        rect_union(&w->dmg, &d);
        w->k->work = 1;
    } else w->stale = 1;
}

/* повідомити лаунчер про вікно: add + буфер + підказки (при появі й при перепідключенні лаунчера) */
static void window_announce(Window *w) {
    Kwl *k = w->k;
    int sc = w->rscale > 0 ? w->rscale : 1;
    if (k->host.window_add)
        k->host.window_add(k->ud, w->id, (w->kind == 1 && w->xw) ? 2 : w->kind, w->parent ? w->parent->id : 0, w->px, w->py, w->w, w->h, sc,
                           w->app_tag, w->app_id ? w->app_id : "", w->title ? w->title : "", w->kind == 0 ? w->has_deco : 0);
    send_buffer(w);
    if (k->host.window_hints && (w->minw || w->minh || w->maxw || w->maxh))
        k->host.window_hints(k->ud, w->id, w->minw, w->minh, w->maxw, w->maxh);
}

static void window_map(Window *w) {
    Kwl *k = w->k; Surface *s = w->root; Buffer *b = s->cur;
    int sc = s->scale > 0 ? s->scale : 1;
    if (w->xw) xwm_pre_map(w);
    if (!region_alloc(w, b->w, b->h, sc)) return;
    w->mapped = 1; w->visible = 1; w->stale = 0; k->nmapped++; k->had_window = 1; k->idle_since = 0;
    w->w = b->w / sc; w->h = b->h / sc;
    window_announce(w);
    w->dmg = EMPTY;
    Rect full = { 0, 0, w->rw, w->rh };
    window_damage_rect(w, full);
    if (w->xw) xwm_post_map(w);
}

static void sel_forget_focus(Kwl *k, Window *w) {
    if (k->kfocus == w) k->kfocus = NULL;
    if (k->pfocus && window_of(k->pfocus) == w) k->pfocus = NULL;
}

static void window_unmap(Window *w) {
    Kwl *k = w->k;
    if (!w->mapped) return;
    w->mapped = 0; k->nmapped--;
    if (k->host.window_remove) k->host.window_remove(k->ud, w->id);
    region_free(w);
    sel_forget_focus(k, w);
    w->dmg = EMPTY;
    if (k->nmapped == 0 && k->had_window) k->idle_since = now_ms() ? now_ms() : 1;
}

static void window_destroy(Window *w) {
    Kwl *k = w->k;
    window_unmap(w);
    xwm_window_gone(w);
    for (Window **pp = &k->windows; *pp; pp = &(*pp)->next) if (*pp == w) { *pp = w->next; break; }
    for (Window *o = k->windows; o; o = o->next) if (o->parent == w) o->parent = NULL;
    if (w->root) { w->root->win = NULL; w->root->role = (w->root->role == 1 || w->root->role == 2) ? 0 : w->root->role; }
    if (w->xs) w->xs->w = NULL;
    Client *c = w->c;
    if (c && !c->dead) {
        Obj *o = c_obj(c, w->top_id); if (o && (o->iface == I_TOPLEVEL || o->iface == I_POPUP)) o->p = NULL;
        o = c_obj(c, w->deco_id); if (w->deco_id && o && o->iface == I_DECO) o->p = NULL;
    }
    free(w->frames); free(w->title); free(w->app_id); region_free(w);
    free(w);
}

static void fire_callback(Client *c, uint32_t id, uint32_t t) {
    msg_start(c, id, 0); put_u32(c, t); msg_end(c, -1);
    obj_gone(c, id);
}

static void win_send_configure(Window *w, int cw, int ch) {
    Client *c = w->c; Kwl *k = w->k;
    if (!c || c->dead || w->xw) return;
    if (w->kind == 0) {
        uint32_t st[4]; int n = 0;
        if (w->maximized) st[n++] = 1;
        if (w->fullscreen) st[n++] = 2;
        if (w->resizing) st[n++] = 3;
        if (k->kfocus == w) st[n++] = 4;
        msg_start(c, w->top_id, 0); put_u32(c, (uint32_t)cw); put_u32(c, (uint32_t)ch); put_arr(c, st, (uint32_t)n * 4); msg_end(c, -1);
    } else {
        msg_start(c, w->top_id, 0); put_u32(c, (uint32_t)w->px); put_u32(c, (uint32_t)w->py); put_u32(c, (uint32_t)cw); put_u32(c, (uint32_t)ch); msg_end(c, -1);
    }
    msg_start(c, w->xs_id, 0); put_u32(c, next_serial(k)); msg_end(c, -1);
    if (w->xs) w->xs->cfg_sent = 1;
}

static void do_tick(Kwl *k) {
    uint32_t t = now_ms();
    int work = 0;
    for (Window *w = k->windows; w; w = w->next) {
        if (w->mapped && !w->visible) continue;
        if (w->mapped && !rect_empty(&w->dmg)) {
            Rect d = w->dmg; w->dmg = EMPTY;
            if (k->host.window_damage) k->host.window_damage(k->ud, w->id, w->gen, d.x0, d.y0, d.x1 - d.x0, d.y1 - d.y0);
        }
        if (w->nframes) {
            Client *c = w->c;
            for (int i = 0; i < w->nframes; i++) if (c && !c->dead) fire_callback(c, w->frames[i], t);
            w->nframes = 0;
        }
    }
    k->last_tick = t; k->work = work;
}

/* ---------- Поверхні ---------- */
static void surface_commit(Client *c, Surface *s) {
    Kwl *k = c->k;
    int old_scale = s->scale;
    if (s->pend_scale > 0) s->scale = s->pend_scale;
    if (!s->scale) s->scale = 1;
    Window *w = window_of(s);
    int is_root = (s->win != NULL);
    Rect dmg = EMPTY;
    int full = 0;

    if (s->has_attach) {
        Buffer *nb = s->pend, *old = s->cur;
        s->pend = NULL; s->has_attach = 0;
        if (nb) nb->refs++;
        s->cur = nb;
        if (old) buf_put(old);
        if (!nb) {
            if (is_root && w) window_unmap(w);
        }
        if (!s->pdmg_given || !old || !nb || old->w != nb->w || old->h != nb->h) full = 1;
    }
    if (s->pdmg_full) full = 1;

    /* відкладені frame-колбеки */
    if (s->npframes) {
        if (w) {
            w->frames = realloc(w->frames, (w->nframes + s->npframes) * sizeof(uint32_t));
            memcpy(w->frames + w->nframes, s->pframes, s->npframes * sizeof(uint32_t));
            w->nframes += s->npframes; k->work = 1;
        } else for (int i = 0; i < s->npframes; i++) fire_callback(c, s->pframes[i], now_ms());
        s->npframes = 0;
    }

    if (!w) {
        /* без вікна (курсор, ще без ролі): буфер не потрібен */
        if (s->cur && s->role != 3) { buf_put(s->cur); s->cur = NULL; }
        s->pdmg = EMPTY; s->pdmg_full = 0; s->pdmg_given = 0;
        return;
    }

    /* початкова конфігурація xdg_surface */
    if (is_root && s->xs && !((XSurf *)s->xs)->cfg_sent && !s->cur) {
        int dw = w->kind == 0 ? (k->out_w * 7 / 10) : w->pw, dh = w->kind == 0 ? (k->out_h * 7 / 10) : w->ph;
        w->cfg_w = dw; w->cfg_h = dh;
        win_send_configure(w, dw, dh);
    }
    if (!s->cur) { s->pdmg = EMPTY; s->pdmg_full = 0; s->pdmg_given = 0; return; }

    if (is_root && !w->mapped) { window_map(w); s->pdmg = EMPTY; s->pdmg_full = 0; s->pdmg_given = 0; return; }
    if (!w->mapped) { s->pdmg = EMPTY; s->pdmg_full = 0; s->pdmg_given = 0; return; }

    if (is_root) {
        int bw = s->cur->w, bh = s->cur->h;
        if (bw != w->rw || bh != w->rh || s->scale != w->rscale) {
            if (region_alloc(w, bw, bh, s->scale)) {
                w->w = bw / s->scale; w->h = bh / s->scale;
                send_buffer(w);
                w->dmg = EMPTY; full = 1;
            }
        }
    }
    int ox, oy; surface_abs(s, &ox, &oy);
    if (full) {
        dmg.x0 = ox; dmg.y0 = oy; dmg.x1 = ox + s->cur->w; dmg.y1 = oy + s->cur->h;
        /* зміна розміру субповерхні/позиції: захопити стару область */
    } else {
        dmg.x0 = ox + s->pdmg.x0; dmg.y0 = oy + s->pdmg.y0; dmg.x1 = ox + s->pdmg.x1; dmg.y1 = oy + s->pdmg.y1;
    }
    (void)old_scale;
    s->pdmg = EMPTY; s->pdmg_full = 0; s->pdmg_given = 0;
    if (!is_root) {
        /* під дитиною могло бути інше: перемалювати від кореня */
    }
    window_damage_rect(w, dmg);
    if (w->visible && !rect_empty(&w->dmg)) k->work = 1;
}

static void surface_detach_kid(Surface *p, Surface *kid) {
    for (int i = 0; i < p->nkids; i++) if (p->kids[i] == kid) { memmove(p->kids + i, p->kids + i + 1, (p->nkids - i - 1) * sizeof(Surface *)); p->nkids--; return; }
}

static void surface_destroy(Client *c, Surface *s) {
    Window *w = window_of(s);
    if (s->win) { Window *ww = s->win; ww->root = NULL; s->win = NULL; window_destroy(ww); w = NULL; }
    if (s->parent) {
        Window *pw = window_of(s->parent);
        int x, y; surface_abs(s, &x, &y);
        Rect d = { x, y, x + (s->cur ? s->cur->w : 0), y + (s->cur ? s->cur->h : 0) };
        surface_detach_kid(s->parent, s);
        if (pw && pw->mapped) window_damage_rect(pw, d);
    }
    for (int i = 0; i < s->nkids; i++) { s->kids[i]->parent = NULL; s->kids[i]->role = 0; }
    if (s->cur) buf_put(s->cur);
    if (c->k->pfocus == s) c->k->pfocus = NULL;
    for (int i = 0; i < s->npframes; i++) obj_gone(c, s->pframes[i]);
    free(s->pframes); free(s->kids);
    if (s->xs) ((XSurf *)s->xs)->s = NULL;
    free(s);
    (void)w;
}

/* ---------- Позиціонер / popup ---------- */
static void popup_place(const Positioner *p, int *x, int *y) {
    int ax = p->ax, ay = p->ay;
    int hl = p->anchor == 3 || p->anchor == 5 || p->anchor == 6, hr = p->anchor == 4 || p->anchor == 7 || p->anchor == 8;
    int vt = p->anchor == 1 || p->anchor == 5 || p->anchor == 7, vb = p->anchor == 2 || p->anchor == 6 || p->anchor == 8;
    int px = ax + (hl ? 0 : hr ? p->aw : p->aw / 2);
    int py = ay + (vt ? 0 : vb ? p->ah : p->ah / 2);
    int gl = p->gravity == 3 || p->gravity == 5 || p->gravity == 6, gr = p->gravity == 4 || p->gravity == 7 || p->gravity == 8;
    int gt = p->gravity == 1 || p->gravity == 5 || p->gravity == 7, gb = p->gravity == 2 || p->gravity == 6 || p->gravity == 8;
    *x = (gl ? px - p->w : gr ? px : px - p->w / 2) + p->ox;
    *y = (gt ? py - p->h : gb ? py : py - p->h / 2) + p->oy;
}

/* ---------- Вхід: допоміжне ---------- */
static Surface *surface_at(Surface *s, double x, double y, double *lx, double *ly, int sc) {
    for (int i = s->nkids - 1; i >= 0; i--) {
        Surface *ch = s->kids[i];
        if (!ch->cur) continue;
        double cx = x - ch->sub_x, cy = y - ch->sub_y;
        int cs = ch->scale > 0 ? ch->scale : 1;
        if (cx >= 0 && cy >= 0 && cx < ch->cur->w / cs && cy < ch->cur->h / cs) return surface_at(ch, cx, cy, lx, ly, sc);
    }
    *lx = x; *ly = y; return s;
}

static void ptr_leave(Kwl *k) {
    Surface *s = k->pfocus;
    if (!s) return;
    Client *c = s->c;
    for (int i = 0; i < c->npointers; i++) {
        Obj *o = c_obj(c, c->pointers[i]); if (!o) continue;
        msg_start(c, c->pointers[i], 1); put_u32(c, next_serial(k)); put_u32(c, s->id); msg_end(c, -1);
        if (o->version >= 5) EV0(c, c->pointers[i], 5);
    }
    k->pfocus = NULL;
}
static void ptr_frame(Client *c) {
    for (int i = 0; i < c->npointers; i++) { Obj *o = c_obj(c, c->pointers[i]); if (o && o->version >= 5) EV0(c, c->pointers[i], 5); }
}
static void ptr_enter(Kwl *k, Surface *s, double lx, double ly) {
    if (k->pfocus == s) return;
    ptr_leave(k);
    Client *c = s->c;
    k->pfocus = s;
    for (int i = 0; i < c->npointers; i++) {
        msg_start(c, c->pointers[i], 0); put_u32(c, next_serial(k)); put_u32(c, s->id); put_u32(c, (uint32_t)fx(lx)); put_u32(c, (uint32_t)fx(ly)); msg_end(c, -1);
    }
    ptr_frame(c);
}

static Surface *ptr_target(Kwl *k, int win, double x, double y, double *lx, double *ly) {
    Window *w = find_window(k, win);
    if (!w || !w->mapped || !w->root) return NULL;
    return surface_at(w->root, x, y, lx, ly, 1);
}

static void send_mods(Kwl *k) {
    Window *w = k->kfocus; if (!w) return;
    Client *c = w->c;
    for (int i = 0; i < c->nkeyboards; i++) {
        msg_start(c, c->keyboards[i], 4); put_u32(c, next_serial(k)); put_u32(c, k->mods_dep); put_u32(c, 0); put_u32(c, k->mods_lock); put_u32(c, (uint32_t)k->group); msg_end(c, -1);
    }
}

static const char *TEXT_MIMES[] = { "text/plain;charset=utf-8", "text/plain", "UTF8_STRING", "STRING", "TEXT" };

static void send_selection(Kwl *k, Client *c) {
    if (!c || c->dead) return;
    for (int i = 0; i < c->nddevs; i++) {
        uint32_t dev = c->ddevs[i];
        if (!c_obj(c, dev)) continue;
        if (!k->sel_src && !k->android_clip) { msg_start(c, dev, 5); put_u32(c, 0); msg_end(c, -1); continue; }
        uint32_t oid = SID_BASE + (c->nsobjs ? 0 : 0);
        /* знайти вільний серверний id */
        uint32_t idx = 0;
        while (idx < c->nsobjs && c->sobjs[idx].iface) idx++;
        oid = SID_BASE + idx;
        DOffer *of = calloc(1, sizeof *of);
        of->c = c; of->id = oid; of->src = k->sel_src; of->android = k->sel_src == NULL;
        if (!c_new(c, oid, I_DOFFER, 3, of)) { free(of); continue; }
        msg_start(c, dev, 0); put_u32(c, oid); msg_end(c, -1);
        if (k->sel_src) { for (int m = 0; m < k->sel_src->nm; m++) { msg_start(c, oid, 0); put_str(c, k->sel_src->mimes[m]); msg_end(c, -1); } }
        else for (size_t m = 0; m < sizeof TEXT_MIMES / sizeof *TEXT_MIMES; m++) { msg_start(c, oid, 0); put_str(c, TEXT_MIMES[m]); msg_end(c, -1); }
        msg_start(c, dev, 5); put_u32(c, oid); msg_end(c, -1);
    }
}

static void set_focus(Kwl *k, Window *w) {
    if (k->kfocus == w) return;
    Window *old = k->kfocus;
    if (old) {
        Client *c = old->c;
        for (int i = 0; i < c->nkeyboards; i++) { msg_start(c, c->keyboards[i], 2); put_u32(c, next_serial(k)); put_u32(c, old->root ? old->root->id : 0); msg_end(c, -1); }
    }
    k->kfocus = w;
    memset(k->down, 0, sizeof k->down);
    if (g_debug) LOG("фокус: вікно %d (%s)", w ? w->id : 0, w && w->xw ? "X11" : "Wayland");
    xwm_focus(k, w);
    if (old && old->mapped && old->kind == 0) win_send_configure(old, old->cfg_w, old->cfg_h);
    if (w && w->root) {
        Client *c = w->c;
        for (int i = 0; i < c->nkeyboards; i++) {
            msg_start(c, c->keyboards[i], 1); put_u32(c, next_serial(k)); put_u32(c, w->root->id); put_arr(c, NULL, 0); msg_end(c, -1);
        }
        send_mods(k);
        send_selection(k, c);
        if (w->mapped && w->kind == 0) win_send_configure(w, w->cfg_w, w->cfg_h);
    }
}

static uint32_t modbit_of(int code) {
    switch (code) { case 42: case 54: return 1; case 29: case 97: return 4; case 56: case 100: return 8; case 125: case 126: return 64; }
    return 0;
}

static void do_key(Kwl *k, int code, int state) {
    Window *w = k->kfocus;
    if (!w || code < 0 || code > 255) return;
    k->down[code] = (unsigned char)state;
    Client *c = w->c;
    uint32_t t = now_ms();
    for (int i = 0; i < c->nkeyboards; i++) {
        msg_start(c, c->keyboards[i], 3); put_u32(c, next_serial(k)); put_u32(c, t); put_u32(c, (uint32_t)code); put_u32(c, (uint32_t)state); msg_end(c, -1);
    }
    uint32_t mb = modbit_of(code), old_d = k->mods_dep, old_l = k->mods_lock;
    if (mb) {
        int any = 0;
        for (int cc = 0; cc < 256; cc++) if (k->down[cc] && modbit_of(cc) == mb) any = 1;
        if (any) k->mods_dep |= mb; else k->mods_dep &= ~mb;
    } else if (code == 58 && state) k->mods_lock ^= 2;
    else if (code == 69 && state) k->mods_lock ^= 16;
    if (old_d != k->mods_dep || old_l != k->mods_lock) send_mods(k);
}

/* ---------- Буфер обміну ---------- */
static int mime_is_text(const char *m) {
    for (size_t i = 0; i < sizeof TEXT_MIMES / sizeof *TEXT_MIMES; i++) if (!strcmp(m, TEXT_MIMES[i])) return 1;
    return 0;
}

static void start_text_read(Kwl *k, DSrc *src) {
    const char *pick = NULL;
    for (size_t i = 0; i < sizeof TEXT_MIMES / sizeof *TEXT_MIMES && !pick; i++)
        for (int m = 0; m < src->nm; m++) if (!strcmp(src->mimes[m], TEXT_MIMES[i])) { pick = src->mimes[m]; break; }
    if (!pick) return;
    int p[2];
    if (pipe2(p, O_CLOEXEC | O_NONBLOCK) < 0) return;
    Client *c = src->c;
    msg_start(c, src->id, 1); put_str(c, pick); msg_end(c, p[1]);   /* fd закриє flush */
    Reader *r = calloc(1, sizeof *r);
    r->fd = p[0]; r->deadline = now_ms() + 3000; r->gen = k->sel_gen; r->next = k->readers; k->readers = r;
}

static void set_selection(Kwl *k, DSrc *src) {
    if (k->sel_src == src && !k->android_clip) return;
    if (k->sel_src && k->sel_src != src && !k->sel_src->dead) { EV0(k->sel_src->c, k->sel_src->id, 2); }
    k->sel_src = src; k->sel_gen++;
    if (src) { free(k->android_clip); k->android_clip = NULL; }
    if (k->kfocus) send_selection(k, k->kfocus->c);
    if (src) start_text_read(k, src);
}

static void write_all_timeout(int fd, const char *s, size_t n) {
    size_t off = 0;
    while (off < n) {
        ssize_t w = write(fd, s + off, n - off);
        if (w > 0) { off += (size_t)w; continue; }
        if (w < 0 && (errno == EAGAIN || errno == EINTR)) {
            struct pollfd pf = { fd, POLLOUT, 0 };
            if (poll(&pf, 1, 500) <= 0) return;
            continue;
        }
        return;
    }
}

static void reader_step(Kwl *k, Reader *r, int *done) {
    char tmp[4096];
    for (;;) {
        ssize_t n = read(r->fd, tmp, sizeof tmp);
        if (n > 0) {
            if (r->len + (size_t)n + 1 > r->cap) { r->cap = (r->len + n + 1) * 2; r->buf = realloc(r->buf, r->cap); }
            memcpy(r->buf + r->len, tmp, (size_t)n); r->len += (size_t)n;
            if (r->len > (4u << 20)) { *done = 1; r->len = 0; return; }
            continue;
        }
        if (n == 0) {
            *done = 1;
            if (r->buf && r->len && r->gen == k->sel_gen) { r->buf[r->len] = 0; if (k->host.clipboard) k->host.clipboard(k->ud, r->buf); }
            return;
        }
        if (errno == EAGAIN) return;
        if (errno == EINTR) continue;
        *done = 1; return;
    }
}

/* ---------- Клієнт: диспетчеризація запитів ---------- */
static void bind_global(Client *c, uint32_t name, const char *iface, uint32_t ver, uint32_t id) {
    Kwl *k = c->k;
    (void)iface;
    switch (name) {
    case G_COMPOSITOR: c_new(c, id, I_COMPOSITOR, ver > 4 ? 4 : ver, NULL); break;
    case G_SUBCOMP: c_new(c, id, I_SUBCOMP, 1, NULL); break;
    case G_SHM:
        c_new(c, id, I_SHM, 1, NULL);
        msg_start(c, id, 0); put_u32(c, 0); msg_end(c, -1);
        msg_start(c, id, 0); put_u32(c, 1); msg_end(c, -1);
        break;
    case G_OUTPUT: {
        int v = ver > 2 ? 2 : (int)ver;
        c_new(c, id, I_OUTPUT, v, NULL);
        if (c->nout < 4) c->outputs[c->nout++] = id;
        msg_start(c, id, 0); put_u32(c, 0); put_u32(c, 0); put_u32(c, 70); put_u32(c, 140); put_u32(c, 0);
        put_str(c, "Kate"); put_str(c, "KateLauncher"); put_u32(c, 0); msg_end(c, -1);
        int osc = c->is_xwl ? 1 : k->scale;
        msg_start(c, id, 1); put_u32(c, 3); put_u32(c, (uint32_t)(k->out_w * osc)); put_u32(c, (uint32_t)(k->out_h * osc)); put_u32(c, 60000); msg_end(c, -1);
        if (v >= 2) { msg_start(c, id, 3); put_u32(c, (uint32_t)osc); msg_end(c, -1); EV0(c, id, 2); }
        break;
    }
    case G_SEAT:
        c->seat_ver = ver > 5 ? 5 : (int)ver;
        c_new(c, id, I_SEAT, c->seat_ver, NULL);
        msg_start(c, id, 0); put_u32(c, 3); msg_end(c, -1);
        if (c->seat_ver >= 2) { msg_start(c, id, 1); put_str(c, "seat0"); msg_end(c, -1); }
        break;
    case G_DDM: c_new(c, id, I_DDM, ver > 3 ? 3 : ver, NULL); break;
    case G_WMBASE: c_new(c, id, I_WMBASE, ver > 2 ? 2 : ver, NULL); break;
    case G_DECOMGR: c_new(c, id, I_DECOMGR, 1, NULL); break;
    default: break;
    }
}

static const struct { const char *n; int v; } GLOBALS[] = {
    { NULL, 0 }, { "wl_compositor", 4 }, { "wl_subcompositor", 1 }, { "wl_shm", 1 }, { "wl_output", 2 },
    { "wl_seat", 5 }, { "wl_data_device_manager", 3 }, { "xdg_wm_base", 2 }, { "zxdg_decoration_manager_v1", 1 }
};

static void list_add(uint32_t *arr, int *n, int max, uint32_t id) { if (*n < max) arr[(*n)++] = id; }
static void list_del(uint32_t *arr, int *n, uint32_t id) {
    for (int i = 0; i < *n; i++) if (arr[i] == id) { memmove(arr + i, arr + i + 1, (*n - i - 1) * sizeof(uint32_t)); (*n)--; return; }
}

static Window *new_window(Client *c, XSurf *xs, int kind, uint32_t top_id) {
    Kwl *k = c->k;
    Window *w = calloc(1, sizeof *w);
    w->k = k; w->c = c; w->id = ++k->next_win; w->root = xs->s; w->xs = xs; w->xs_id = xs->id; w->top_id = top_id;
    w->kind = kind; w->rfd = -1; w->rscale = 1; w->visible = 1; w->dmg = EMPTY;
    strncpy(w->app_tag, c->app_tag, sizeof w->app_tag - 1);
    w->next = k->windows; k->windows = w;
    xs->w = w;
    if (xs->s) { xs->s->win = w; xs->s->role = kind == 0 ? 1 : 2; }
    return w;
}

static void dispatch(Client *c, uint32_t id, uint32_t op, const uint8_t *p, size_t n) {
    Kwl *k = c->k;
    Obj *o = c_obj(c, id);
    Rd r = { p, p + n, c, 0 };
    if (!o) { LOG("msg for unknown object %u", id); return; }
    switch (o->iface) {
    case I_DISPLAY:
        if (op == 0) { uint32_t cb = rd_u32(&r); c_new(c, cb, I_CALLBACK, 1, NULL); msg_start(c, cb, 0); put_u32(c, next_serial(k)); msg_end(c, -1); obj_gone(c, cb); }
        else if (op == 1) {
            uint32_t rid = rd_u32(&r); c_new(c, rid, I_REGISTRY, 1, NULL);
            for (uint32_t g = 1; g <= 8; g++) { msg_start(c, rid, 0); put_u32(c, g); put_str(c, GLOBALS[g].n); put_u32(c, (uint32_t)GLOBALS[g].v); msg_end(c, -1); }
        }
        break;
    case I_REGISTRY:
        if (op == 0) { uint32_t name = rd_u32(&r); const char *iface = rd_str(&r); uint32_t ver = rd_u32(&r), nid = rd_u32(&r); if (!r.err && name >= 1 && name <= 8) bind_global(c, name, iface, ver, nid); }
        break;
    case I_COMPOSITOR:
        if (op == 0) {
            uint32_t sid = rd_u32(&r);
            Surface *s = calloc(1, sizeof *s); s->c = c; s->id = sid; s->scale = 1; s->pend_scale = 1; s->pdmg = EMPTY;
            c_new(c, sid, I_SURFACE, 4, s);
        } else if (op == 1) { uint32_t rid = rd_u32(&r); c_new(c, rid, I_REGION, 1, NULL); }
        break;
    case I_REGION: if (op == 0) obj_gone(c, id); break;
    case I_SHM:
        if (op == 0) {
            uint32_t pid_ = rd_u32(&r); int fd = rd_fd(&r); int32_t size = rd_i32(&r);
            if (r.err || fd < 0 || size <= 0) { if (fd >= 0) close(fd); break; }
            uint8_t *m = mmap(NULL, (size_t)size, PROT_READ, MAP_SHARED, fd, 0);
            if (m == MAP_FAILED) { close(fd); break; }
            Pool *pl = calloc(1, sizeof *pl); pl->fd = fd; pl->data = m; pl->size = (size_t)size; pl->refs = 1;
            c_new(c, pid_, I_POOL, 1, pl);
        }
        break;
    case I_POOL: {
        Pool *pl = o->p;
        if (op == 0) {
            uint32_t bid = rd_u32(&r); int32_t off = rd_i32(&r), w = rd_i32(&r), h = rd_i32(&r), st = rd_i32(&r); uint32_t fmt = rd_u32(&r);
            if (r.err || off < 0 || w <= 0 || h <= 0 || st < w * 4 || (st & 3) || (off & 3) || (fmt != 0 && fmt != 1) ||
                (size_t)off + (size_t)st * (size_t)(h - 1) + (size_t)w * 4 > pl->size) break;
            Buffer *b = calloc(1, sizeof *b); b->pool = pl; pl->refs++; b->off = off; b->w = w; b->h = h; b->stride = st; b->fmt = fmt; b->c = c; b->id = bid;
            c_new(c, bid, I_BUFFER, 1, b);
        } else if (op == 1) {
            if (--pl->refs <= 0) { munmap(pl->data, pl->size); close(pl->fd); free(pl); }
            obj_gone(c, id);
        } else if (op == 2) {
            int32_t sz = rd_i32(&r);
            if (!r.err && sz > 0 && (size_t)sz > pl->size) {
                uint8_t *m = mmap(NULL, (size_t)sz, PROT_READ, MAP_SHARED, pl->fd, 0);
                if (m != MAP_FAILED) { munmap(pl->data, pl->size); pl->data = m; pl->size = (size_t)sz; }
            }
        }
        break;
    }
    case I_BUFFER:
        if (op == 0) {
            Buffer *b = o->p;
            for (uint32_t i = 0; i < c->nobjs; i++) if (c->objs[i].iface == I_SURFACE) { Surface *s = c->objs[i].p; if (s->pend == b) { s->pend = NULL; s->has_attach = 0; } }
            b->dead = 1;
            if (b->refs <= 0) buf_free(b);
            obj_gone(c, id);
        }
        break;
    case I_SURFACE: {
        Surface *s = o->p;
        switch (op) {
        case 0: surface_destroy(c, s); obj_gone(c, id); break;
        case 1: { uint32_t bid = rd_u32(&r); rd_i32(&r); rd_i32(&r);
            Obj *bo = bid ? c_obj(c, bid) : NULL;
            s->pend = bo && bo->iface == I_BUFFER ? bo->p : NULL; s->has_attach = 1; break; }
        case 2: { int x = rd_i32(&r), y = rd_i32(&r), w = rd_i32(&r), h = rd_i32(&r); int sc = s->pend_scale > 0 ? s->pend_scale : 1;
            Rect d = { x * sc, y * sc, (x + w) * sc, (y + h) * sc }; if (!r.err) { rect_union(&s->pdmg, &d); s->pdmg_given = 1; } break; }
        case 3: { uint32_t cb = rd_u32(&r); c_new(c, cb, I_CALLBACK, 1, NULL);
            s->pframes = realloc(s->pframes, (s->npframes + 1) * sizeof(uint32_t)); s->pframes[s->npframes++] = cb; break; }
        case 6: surface_commit(c, s); break;
        case 8: { int32_t sc = rd_i32(&r); if (!r.err && sc > 0 && sc <= 8) s->pend_scale = sc; break; }
        case 9: { int x = rd_i32(&r), y = rd_i32(&r), w = rd_i32(&r), h = rd_i32(&r); Rect d = { x, y, x + w, y + h }; if (!r.err) { rect_union(&s->pdmg, &d); s->pdmg_given = 1; } break; }
        default: break;
        }
        break;
    }
    case I_SUBCOMP:
        if (op == 1) {
            uint32_t sid = rd_u32(&r), sfc = rd_u32(&r), par = rd_u32(&r);
            Obj *so = c_obj(c, sfc), *po = c_obj(c, par);
            if (r.err || !so || !po || so->iface != I_SURFACE || po->iface != I_SURFACE) break;
            Surface *s = so->p, *pa = po->p;
            if (s->role && s->role != 3) break;
            s->role = 3; s->parent = pa; s->sub_id = sid;
            pa->kids = realloc(pa->kids, (pa->nkids + 1) * sizeof(Surface *)); pa->kids[pa->nkids++] = s;
            c_new(c, sid, I_SUBSURFACE, 1, s);
        } else if (op == 0) obj_gone(c, id);
        break;
    case I_SUBSURFACE: {
        Surface *s = o->p;
        if (op == 0) { /* destroy: залишаємо поверхню, відчіплюємо */
            if (s->parent) { Window *w = window_of(s->parent); int x, y; surface_abs(s, &x, &y);
                Rect d = { x, y, x + (s->cur ? s->cur->w : 0), y + (s->cur ? s->cur->h : 0) };
                surface_detach_kid(s->parent, s); s->parent = NULL; s->role = 0; if (w && w->mapped) window_damage_rect(w, d); }
            obj_gone(c, id);
        } else if (op == 1 && s->parent) {
            int x = rd_i32(&r), y = rd_i32(&r); Window *w = window_of(s);
            if (r.err) break;
            int ox, oy; surface_abs(s, &ox, &oy);
            Rect d0 = { ox, oy, ox + (s->cur ? s->cur->w : 0), oy + (s->cur ? s->cur->h : 0) };
            s->sub_x = x; s->sub_y = y;
            surface_abs(s, &ox, &oy);
            Rect d1 = { ox, oy, ox + (s->cur ? s->cur->w : 0), oy + (s->cur ? s->cur->h : 0) };
            if (w && w->mapped) { window_damage_rect(w, d0); window_damage_rect(w, d1); }
        } else if ((op == 2 || op == 3) && s->parent) {
            uint32_t sib = rd_u32(&r); Obj *so = c_obj(c, sib); Surface *sb = so && so->iface == I_SURFACE ? so->p : NULL;
            Surface *pa = s->parent; if (!sb) break;
            surface_detach_kid(pa, s);
            int idx = -1; for (int i = 0; i < pa->nkids; i++) if (pa->kids[i] == sb) idx = i;
            if (sb == pa) idx = op == 2 ? -1 : 0; else if (idx >= 0) idx += op == 2 ? 1 : 0; else idx = pa->nkids;
            if (idx < 0) idx = 0; if (idx > pa->nkids) idx = pa->nkids;
            pa->kids = realloc(pa->kids, (pa->nkids + 1) * sizeof(Surface *));
            memmove(pa->kids + idx + 1, pa->kids + idx, (pa->nkids - idx) * sizeof(Surface *));
            pa->kids[idx] = s; pa->nkids++;
        }
        break;
    }
    case I_OUTPUT: if (op == 0) { list_del(c->outputs, &c->nout, id); obj_gone(c, id); } break;
    case I_SEAT:
        if (op == 0) { uint32_t pid_ = rd_u32(&r); c_new(c, pid_, I_POINTER, o->version, NULL); list_add(c->pointers, &c->npointers, 8, pid_); }
        else if (op == 1) {
            uint32_t kid = rd_u32(&r); c_new(c, kid, I_KEYBOARD, o->version, NULL); list_add(c->keyboards, &c->nkeyboards, 8, kid);
            int fd = mkshm(k->keymap_len + 1);
            if (fd >= 0) {
                void *m = mmap(NULL, k->keymap_len + 1, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
                if (m != MAP_FAILED) { memcpy(m, k->keymap, k->keymap_len + 1); munmap(m, k->keymap_len + 1); }
                msg_start(c, kid, 0); put_u32(c, 1); put_u32(c, (uint32_t)(k->keymap_len + 1)); msg_end(c, fd);
            }
            if (o->version >= 4) { msg_start(c, kid, 5); put_u32(c, 25); put_u32(c, 400); msg_end(c, -1); }
            if (k->kfocus && k->kfocus->c == c && k->kfocus->root) {
                msg_start(c, kid, 1); put_u32(c, next_serial(k)); put_u32(c, k->kfocus->root->id); put_arr(c, NULL, 0); msg_end(c, -1);
                msg_start(c, kid, 4); put_u32(c, next_serial(k)); put_u32(c, k->mods_dep); put_u32(c, 0); put_u32(c, k->mods_lock); put_u32(c, (uint32_t)k->group); msg_end(c, -1);
            }
        } else if (op == 2) { uint32_t tid = rd_u32(&r); c_new(c, tid, I_NONE + I_REGION, 1, NULL); }
        else if (op == 3) obj_gone(c, id);
        break;
    case I_POINTER:
        if (op == 1) { list_del(c->pointers, &c->npointers, id); obj_gone(c, id); }
        else if (op == 0) { /* set_cursor: курсор не малюємо */ }
        break;
    case I_KEYBOARD: if (op == 0) { list_del(c->keyboards, &c->nkeyboards, id); obj_gone(c, id); } break;
    case I_DDM:
        if (op == 0) {
            uint32_t sid = rd_u32(&r); DSrc *d = calloc(1, sizeof *d); d->c = c; d->id = sid; c_new(c, sid, I_DSRC, 3, d);
        } else if (op == 1) {
            uint32_t did = rd_u32(&r); c_new(c, did, I_DDEV, o->version, NULL); list_add(c->ddevs, &c->nddevs, 8, did);
            if (k->kfocus && k->kfocus->c == c) send_selection(k, c);
        }
        break;
    case I_DSRC: {
        DSrc *d = o->p;
        if (op == 0) { const char *m = rd_str(&r); if (m && d->nm < 16) d->mimes[d->nm++] = strdup(m); }
        else if (op == 1) {
            if (k->sel_src == d) { k->sel_src = NULL; k->sel_gen++; if (k->kfocus) send_selection(k, k->kfocus->c); }
            for (int i = 0; i < d->nm; i++) free(d->mimes[i]);
            for (uint32_t i = 0; i < c->nsobjs; i++) if (c->sobjs[i].iface == I_DOFFER) { DOffer *of = c->sobjs[i].p; if (of && of->src == d) of->src = NULL; }
            for (Client *oc = k->clients; oc; oc = oc->next) for (uint32_t i = 0; i < oc->nsobjs; i++) if (oc->sobjs[i].iface == I_DOFFER) { DOffer *of = oc->sobjs[i].p; if (of && of->src == d) of->src = NULL; }
            free(d); obj_gone(c, id);
        }
        break;
    }
    case I_DDEV:
        if (op == 1) {
            uint32_t sid = rd_u32(&r); Obj *so = sid ? c_obj(c, sid) : NULL;
            if (!r.err) set_selection(k, so && so->iface == I_DSRC ? so->p : NULL);
        } else if (op == 0) { uint32_t sid = rd_u32(&r); Obj *so = sid ? c_obj(c, sid) : NULL; if (so && so->iface == I_DSRC) EV0(c, sid, 2); }
        else if (op == 2) { list_del(c->ddevs, &c->nddevs, id); obj_gone(c, id); }
        break;
    case I_DOFFER: {
        DOffer *of = o->p;
        if (op == 1) {
            const char *mime = rd_str(&r); int fd = rd_fd(&r);
            if (r.err || fd < 0) { if (fd >= 0) close(fd); break; }
            if (of->src && !of->src->dead) { msg_start(of->src->c, of->src->id, 1); put_str(of->src->c, mime); msg_end(of->src->c, fd); }
            else if (of->android && k->android_clip) { write_all_timeout(fd, k->android_clip, strlen(k->android_clip)); close(fd); }
            else close(fd);
        } else if (op == 2) { free(of); c_free_obj(c, id); }
        break;
    }
    case I_WMBASE:
        if (op == 1) { uint32_t pid_ = rd_u32(&r); Positioner *po = calloc(1, sizeof *po); c_new(c, pid_, I_POSITIONER, 1, po); }
        else if (op == 2) {
            uint32_t xid = rd_u32(&r), sfc = rd_u32(&r); Obj *so = c_obj(c, sfc);
            if (r.err || !so || so->iface != I_SURFACE) break;
            Surface *s = so->p; XSurf *xs = calloc(1, sizeof *xs); xs->c = c; xs->id = xid; xs->s = s; s->xs = xs;
            c_new(c, xid, I_XSURF, o->version, xs);
        }
        break;
    case I_POSITIONER: {
        Positioner *po = o->p;
        switch (op) {
        case 0: free(po); c_free_obj(c, id); break;
        case 1: po->w = rd_i32(&r); po->h = rd_i32(&r); break;
        case 2: po->ax = rd_i32(&r); po->ay = rd_i32(&r); po->aw = rd_i32(&r); po->ah = rd_i32(&r); break;
        case 3: po->anchor = (int)rd_u32(&r); break;
        case 4: po->gravity = (int)rd_u32(&r); break;
        case 6: po->ox = rd_i32(&r); po->oy = rd_i32(&r); break;
        default: break;
        }
        break;
    }
    case I_XSURF: {
        XSurf *xs = o->p;
        if (op == 0) { if (xs->s) xs->s->xs = NULL; free(xs); c_free_obj(c, id); send_delete_id(c, id); }
        else if (op == 1) {
            uint32_t tid = rd_u32(&r);
            if (!xs->s || xs->w) break;
            Window *w = new_window(c, xs, 0, tid);
            c_new(c, tid, I_TOPLEVEL, o->version, w);
        } else if (op == 2) {
            uint32_t tid = rd_u32(&r), par = rd_u32(&r), pos = rd_u32(&r);
            Obj *pxo = par ? c_obj(c, par) : NULL, *poo = c_obj(c, pos);
            if (!xs->s || xs->w || !poo || poo->iface != I_POSITIONER) break;
            Window *w = new_window(c, xs, 1, tid);
            XSurf *px = pxo && pxo->iface == I_XSURF ? pxo->p : NULL;
            w->parent = px ? px->w : NULL;
            Positioner *pp = poo->p; int x, y; popup_place(pp, &x, &y);
            w->px = x + (px && px->has_geom ? px->gx : 0); w->py = y + (px && px->has_geom ? px->gy : 0); w->pw = pp->w; w->ph = pp->h;
            c_new(c, tid, I_POPUP, o->version, w);
        } else if (op == 3) { xs->gx = rd_i32(&r); xs->gy = rd_i32(&r); xs->gw = rd_i32(&r); xs->gh = rd_i32(&r); xs->has_geom = !r.err; }
        break;
    }
    case I_TOPLEVEL: {
        Window *w = o->p;
        if (op == 0) { if (w) window_destroy(w); obj_gone(c, id); break; }
        if (!w) break;
        switch (op) {
        case 2: { const char *t = rd_str(&r); if (t) { free(w->title); w->title = strdup(t); if (w->mapped && k->host.window_title) k->host.window_title(k->ud, w->id, t); } break; }
        case 3: { const char *t = rd_str(&r); if (t) { free(w->app_id); w->app_id = strdup(t); if (w->mapped && k->host.window_appid) k->host.window_appid(k->ud, w->id, t); } break; }
        case 5: if (k->host.window_request) k->host.window_request(k->ud, w->id, KWL_REQ_MOVE, 0); break;
        case 6: { rd_u32(&r); rd_u32(&r); uint32_t e = rd_u32(&r); if (k->host.window_request) k->host.window_request(k->ud, w->id, KWL_REQ_RESIZE, (int)e); break; }
        case 7: { int a = rd_i32(&r), b = rd_i32(&r); if (a == w->maxw && b == w->maxh) break; w->maxw = a; w->maxh = b; if (w->mapped && k->host.window_hints) k->host.window_hints(k->ud, w->id, w->minw, w->minh, w->maxw, w->maxh); break; }
        case 8: { int a = rd_i32(&r), b = rd_i32(&r); if (a == w->minw && b == w->minh) break; w->minw = a; w->minh = b; if (w->mapped && k->host.window_hints) k->host.window_hints(k->ud, w->id, w->minw, w->minh, w->maxw, w->maxh); break; }
        case 9: if (k->host.window_request) k->host.window_request(k->ud, w->id, KWL_REQ_MAXIMIZE, 1); break;
        case 10: if (k->host.window_request) k->host.window_request(k->ud, w->id, KWL_REQ_MAXIMIZE, 0); break;
        case 11: if (k->host.window_request) k->host.window_request(k->ud, w->id, KWL_REQ_FULLSCREEN, 1); break;
        case 12: if (k->host.window_request) k->host.window_request(k->ud, w->id, KWL_REQ_FULLSCREEN, 0); break;
        case 13: if (k->host.window_request) k->host.window_request(k->ud, w->id, KWL_REQ_MINIMIZE, 1); break;
        default: break;
        }
        break;
    }
    case I_POPUP: {
        Window *w = o->p;
        if (op == 0) { if (w) window_destroy(w); obj_gone(c, id); }
        else if (op == 1 && w) w->grab = 1;
        break;
    }
    case I_DECOMGR:
        if (op == 1) {
            uint32_t did = rd_u32(&r), tid = rd_u32(&r); Obj *to = c_obj(c, tid);
            if (r.err || !to || to->iface != I_TOPLEVEL) break;
            Window *w = to->p; c_new(c, did, I_DECO, 1, w);
            if (w) { w->has_deco = 1; w->deco_id = did; }
            msg_start(c, did, 0); put_u32(c, 2); msg_end(c, -1);
        } else if (op == 0) obj_gone(c, id);
        break;
    case I_DECO:
        if (op == 0) { Window *w = o->p; if (w) { w->has_deco = 0; w->deco_id = 0; } obj_gone(c, id); }
        else if (op == 1 || op == 2) { msg_start(c, id, 0); put_u32(c, 2); msg_end(c, -1); }
        break;
    default: break;
    }
}

/* ---------- Клієнти ---------- */
static void read_app_tag(Client *c) {
    struct ucred cr; socklen_t l = sizeof cr;
    c->pid = 0; c->app_tag[0] = 0;
    if (getsockopt(c->fd, SOL_SOCKET, SO_PEERCRED, &cr, &l) < 0) return;
    c->pid = cr.pid;
    char path[64]; snprintf(path, sizeof path, "/proc/%d/comm", (int)cr.pid);
    int cfd = open(path, O_RDONLY | O_CLOEXEC);
    if (cfd >= 0) { char cm[32]; ssize_t cn = read(cfd, cm, sizeof cm - 1); close(cfd); if (cn > 0) { cm[cn] = 0; if (!strncmp(cm, "Xwayland", 8)) c->is_xwl = 1; } }
    snprintf(path, sizeof path, "/proc/%d/environ", (int)cr.pid);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return;
    static char buf[65536];
    ssize_t n = read(fd, buf, sizeof buf - 1); close(fd);
    if (n <= 0) return;
    buf[n] = 0;
    for (ssize_t i = 0; i < n;) {
        size_t len = strlen(buf + i);
        if (!strncmp(buf + i, "KATE_APP_ID=", 12)) strncpy(c->app_tag, buf + i + 12, sizeof c->app_tag - 1);
        else if (!strcmp(buf + i, "KATE_XWL=1")) c->is_xwl = 1;
        i += (ssize_t)len + 1;
    }
}

static void client_free(Client *c) {
    Kwl *k = c->k;
    for (Window *w = k->windows, *nx; w; w = nx) { nx = w->next; if (w->c == c) window_destroy(w); }
    if (k->sel_src && k->sel_src->c == c) { k->sel_src = NULL; k->sel_gen++; }
    for (uint32_t i = 0; i < c->nobjs; i++) {
        Obj *o = &c->objs[i];
        if (!o->iface) continue;
        switch (o->iface) {
        case I_SURFACE: { Surface *s = o->p; s->win = NULL; if (k->pfocus == s) k->pfocus = NULL;
            for (int j = 0; j < s->nkids; j++) s->kids[j]->parent = NULL;
            free(s->pframes); free(s->kids); if (s->xs) ((XSurf *)s->xs)->s = NULL; free(s); break; }
        case I_XSURF: free(o->p); break;
        case I_POSITIONER: free(o->p); break;
        case I_DSRC: { DSrc *d = o->p; for (int j = 0; j < d->nm; j++) free(d->mimes[j]); free(d); break; }
        case I_BUFFER: { Buffer *b = o->p; b->refs = 0; b->dead = 1; buf_free(b); break; }
        case I_POOL: { Pool *pl = o->p; if (--pl->refs <= 0) { munmap(pl->data, pl->size); close(pl->fd); free(pl); } break; }
        default: break;
        }
    }
    for (uint32_t i = 0; i < c->nsobjs; i++) if (c->sobjs[i].iface == I_DOFFER) free(c->sobjs[i].p);
    for (int i = 0; i < c->nofd; i++) close(c->ofds[i]);
    for (int i = 0; i < c->nifd; i++) close(c->ifds[i]);
    free(c->objs); free(c->sobjs); free(c->ib); free(c->ob);
    close(c->fd);
    free(c);
}

static void client_read(Client *c) {
    for (;;) {
        if (c->ilen + 4096 > c->icap) {
            size_t nc = c->icap ? c->icap * 2 : 16384;
            uint8_t *t = realloc(c->ib, nc); if (!t) { c->dead = 1; return; }
            c->ib = t; c->icap = nc;
        }
        char cb[CMSG_SPACE(sizeof(int) * 16)];
        struct iovec iov = { c->ib + c->ilen, c->icap - c->ilen };
        struct msghdr mh; memset(&mh, 0, sizeof mh);
        mh.msg_iov = &iov; mh.msg_iovlen = 1; mh.msg_control = cb; mh.msg_controllen = sizeof cb;
        ssize_t n = recvmsg(c->fd, &mh, MSG_DONTWAIT | MSG_CMSG_CLOEXEC);
        if (n < 0) { if (errno == EAGAIN) break; if (errno == EINTR) continue; c->dead = 1; return; }
        if (n == 0) { c->dead = 1; return; }
        for (struct cmsghdr *cm = CMSG_FIRSTHDR(&mh); cm; cm = CMSG_NXTHDR(&mh, cm)) {
            if (cm->cmsg_level == SOL_SOCKET && cm->cmsg_type == SCM_RIGHTS) {
                int cnt = (int)((cm->cmsg_len - CMSG_LEN(0)) / sizeof(int));
                int *fds = (int *)CMSG_DATA(cm);
                for (int i = 0; i < cnt; i++) { if (c->nifd < 32) c->ifds[c->nifd++] = fds[i]; else close(fds[i]); }
            }
        }
        c->ilen += (size_t)n;
        size_t off = 0;
        while (c->ilen - off >= 8) {
            uint32_t id, w1; memcpy(&id, c->ib + off, 4); memcpy(&w1, c->ib + off + 4, 4);
            uint32_t size = w1 >> 16, op = w1 & 0xffff;
            if (size < 8 || size > MAXMSG) { c->dead = 1; return; }
            if (c->ilen - off < size) break;
            dispatch(c, id, op, c->ib + off + 8, size - 8);
            off += size;
            if (c->dead) return;
        }
        if (off) { memmove(c->ib, c->ib + off, c->ilen - off); c->ilen -= off; }
        if ((size_t)n < iov.iov_len) break;
    }
}

#include "xwm.h"

/* ---------- Команди ззовні ---------- */
static void apply_cmd(Kwl *k, Cmd *m) {
    switch (m->op) {
    case KWL_CMD_POINTER_MOVE: {
        double lx, ly; Surface *s = ptr_target(k, m->a, m->x, m->y, &lx, &ly);
        if (!s) { ptr_leave(k); break; }
        if (k->pfocus != s) ptr_enter(k, s, lx, ly);
        Client *c = s->c;
        for (int i = 0; i < c->npointers; i++) { msg_start(c, c->pointers[i], 2); put_u32(c, now_ms()); put_u32(c, (uint32_t)fx(lx)); put_u32(c, (uint32_t)fx(ly)); msg_end(c, -1); }
        ptr_frame(c);
        break;
    }
    case KWL_CMD_POINTER_BUTTON: {
        double lx, ly; Surface *s = ptr_target(k, m->a, m->x, m->y, &lx, &ly);
        if (g_debug) LOG("кнопка %d %s вікно=%d (%.0f,%.0f) -> поверхня %s фокус-клавіатури=%d", m->b, m->c ? "вниз" : "вгору", m->a, m->x, m->y, s ? "є" : "НЕМАЄ", k->kfocus ? k->kfocus->id : 0);
        if (!s) break;
        if (k->pfocus != s) ptr_enter(k, s, lx, ly);
        Client *c = s->c;
        for (int i = 0; i < c->npointers; i++) {
            msg_start(c, c->pointers[i], 2); put_u32(c, now_ms()); put_u32(c, (uint32_t)fx(lx)); put_u32(c, (uint32_t)fx(ly)); msg_end(c, -1);
            msg_start(c, c->pointers[i], 3); put_u32(c, next_serial(k)); put_u32(c, now_ms()); put_u32(c, (uint32_t)m->b); put_u32(c, (uint32_t)m->c); msg_end(c, -1);
        }
        ptr_frame(c);
        break;
    }
    case KWL_CMD_POINTER_AXIS: {
        Surface *s = k->pfocus; if (!s) break;
        Client *c = s->c;
        for (int i = 0; i < c->npointers; i++) {
            Obj *o = c_obj(c, c->pointers[i]); if (!o) continue;
            if (o->version >= 5) { msg_start(c, c->pointers[i], 6); put_u32(c, m->c ? 0 : 2); msg_end(c, -1);
                if (m->c) { msg_start(c, c->pointers[i], 8); put_u32(c, (uint32_t)m->b); put_u32(c, (uint32_t)m->c); msg_end(c, -1); } }
            msg_start(c, c->pointers[i], 4); put_u32(c, now_ms()); put_u32(c, (uint32_t)m->b); put_u32(c, (uint32_t)fx(m->x)); msg_end(c, -1);
        }
        ptr_frame(c);
        break;
    }
    case KWL_CMD_POINTER_LEAVE: ptr_leave(k); break;
    case KWL_CMD_KEY: if (g_debug) LOG("клавіша %d %s фокус=%d", m->a, m->b ? "вниз" : "вгору", k->kfocus ? k->kfocus->id : 0); do_key(k, m->a, m->b); break;
    case KWL_CMD_GROUP: if (k->group != m->a) { k->group = m->a; send_mods(k); } break;
    case KWL_CMD_FOCUS: { Window *w = m->a ? find_window(k, m->a) : NULL; if (m->a && !w) break; set_focus(k, w); break; }
    case KWL_CMD_CONFIGURE: {
        Window *w = find_window(k, m->a); if (!w || !w->mapped) break;
        w->maximized = m->d & 1; w->fullscreen = (m->d >> 1) & 1; w->resizing = (m->d >> 2) & 1;
        w->cfg_w = m->b; w->cfg_h = m->c;
        if (w->xw) { xwm_configure(w, m->b, m->c, m->d); break; }
        win_send_configure(w, m->b, m->c);
        break;
    }
    case KWL_CMD_CLOSE: {
        Window *w = find_window(k, m->a); if (!w || w->c->dead) break;
        if (w->xw) { xwm_close(w); break; }
        EV0(w->c, w->top_id, 1);
        break;
    }
    case KWL_CMD_VISIBLE: {
        Window *w = find_window(k, m->a); if (!w) break;
        int v = m->b != 0;
        if (v == w->visible) break;
        w->visible = v;
        if (v) {
            if (w->stale && w->mapped) { w->stale = 0; Rect full = { 0, 0, w->rw, w->rh }; window_damage_rect(w, full); }
            k->work = 1;
        }
        break;
    }
    case KWL_CMD_OUTPUT: {
        k->out_w = m->a > 0 ? m->a : k->out_w; k->out_h = m->b > 0 ? m->b : k->out_h; k->scale = m->c > 0 && m->c <= 8 ? m->c : k->scale;
        for (Client *c = k->clients; c; c = c->next)
            for (int i = 0; i < c->nout; i++) {
                uint32_t id = c->outputs[i];
                int osc = c->is_xwl ? 1 : k->scale;
                msg_start(c, id, 1); put_u32(c, 3); put_u32(c, (uint32_t)(k->out_w * osc)); put_u32(c, (uint32_t)(k->out_h * osc)); put_u32(c, 60000); msg_end(c, -1);
                msg_start(c, id, 3); put_u32(c, (uint32_t)osc); msg_end(c, -1);
                EV0(c, id, 2);
            }
        break;
    }
    case KWL_CMD_FPS: k->fps = m->a < 1 ? 1 : m->a > 240 ? 240 : m->a; break;
    case KWL_CMD_CLIPBOARD:
        if (m->s) {
            if (k->sel_src && k->sel_src->c) { EV0(k->sel_src->c, k->sel_src->id, 2); }
            k->sel_src = NULL; k->sel_gen++;
            free(k->android_clip); k->android_clip = strdup(m->s);
            if (k->kfocus) send_selection(k, k->kfocus->c);
        }
        break;
    case KWL_CMD_DISMISS_POPUPS:
        for (Window *w = k->windows; w; w = w->next) if (w->kind == 1 && w->grab && !w->c->dead) EV0(w->c, w->top_id, 1);
        break;
    case KWL_CMD_STOP: k->stop = 1; break;
    case KWL_CMD_XWM:
        if (m->s && m->s[0]) { snprintf(k->xwm_path, sizeof k->xwm_path, "%s", m->s); k->xwm_next_try = 0; k->xwm_deadline = now_ms() + 90000; if (k->xwm) xwm_close_conn(k); LOG("xwm: чекаю X-сервер на %s", k->xwm_path); }
        break;
    case KWL_CMD_XWM_STOP: k->xwm_path[0] = 0; if (k->xwm) xwm_close_conn(k); break;
    case KWL_CMD_DEBUG: g_debug = m->a != 0; break;
    case KWL_CMD_IDLE: k->idle_ms = m->a < 0 ? 0 : m->a; k->idle_since = k->nmapped == 0 && k->had_window ? now_ms() : 0; break;
    case KWL_CMD_RESYNC:   /* лаунчер перепідключився: розповісти про всі вікна наново */
        for (int pass = 0; pass < 2; pass++)
            for (Window *w = k->windows; w; w = w->next)
                if (w->mapped && (pass == 0) == (w->kind == 0)) {
                    window_announce(w);
                    Rect full = { 0, 0, w->rw, w->rh };
                    rect_union(&w->dmg, &full); k->work = 1;
                }
        break;
    case KWL_CMD_ACTIVITY: k->grace_until = now_ms() + (uint32_t)(m->a > 0 ? m->a : 30) * 1000u; break;
    }
}

void kwl_cmd(Kwl *k, int op, int a, int b, int c, int d, double x, double y, const char *s) {
    pthread_mutex_lock(&k->qm);
    if (k->qn == k->qcap) { k->qcap = k->qcap ? k->qcap * 2 : 64; k->q = realloc(k->q, k->qcap * sizeof(Cmd)); }
    Cmd *m = &k->q[k->qn++];
    m->op = op; m->a = a; m->b = b; m->c = c; m->d = d; m->x = x; m->y = y; m->s = s ? strdup(s) : NULL;
    pthread_mutex_unlock(&k->qm);
    uint64_t one = 1; if (write(k->efd, &one, 8) < 0) { /* ignore */ }
}

static void drain_cmds(Kwl *k) {
    uint64_t v; if (read(k->efd, &v, 8) < 0) { /* ignore */ }
    for (;;) {
        pthread_mutex_lock(&k->qm);
        if (!k->qn) { pthread_mutex_unlock(&k->qm); break; }
        Cmd m = k->q[0]; memmove(k->q, k->q + 1, (k->qn - 1) * sizeof(Cmd)); k->qn--;
        pthread_mutex_unlock(&k->qm);
        apply_cmd(k, &m);
        free(m.s);
    }
}

/* ---------- Створення і цикл ---------- */

/* Скрипт запуску торкається $dir/launching перед стартом програми: поки вона підіймається в середовищі
 * (proot-distro, wine...), композитор не завершується через простій. */
static int launch_recent(Kwl *k) {
    char p[352]; struct stat st;
    snprintf(p, sizeof p, "%s/launching", k->dir);
    if (stat(p, &st) < 0) return 0;
    return time(NULL) - st.st_mtime < 45;
}

Kwl *kwl_create(const KwlHost *host, void *ud, const char *dir, const char *sock, const char *keymap,
                int out_w, int out_h, int scale, int fps) {
    g_debug = getenv("KWL_DEBUG") != NULL;
    Kwl *k = calloc(1, sizeof *k);
    k->host = *host; k->ud = ud;
    k->out_w = out_w > 0 ? out_w : 800; k->out_h = out_h > 0 ? out_h : 600; k->scale = scale > 0 ? scale : 1; k->fps = fps > 0 ? fps : 30;
    k->keymap = strdup(keymap ? keymap : ""); k->keymap_len = strlen(k->keymap);
    pthread_mutex_init(&k->qm, NULL);
    snprintf(k->dir, sizeof k->dir, "%s", dir);
    snprintf(k->sockpath, sizeof k->sockpath, "%s/%s", dir, sock);
    snprintf(k->pidpath, sizeof k->pidpath, "%s/host.pid", dir);
    mkdir(dir, 0700);
    k->efd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    k->lfd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (k->efd < 0 || k->lfd < 0) { free(k); return NULL; }
    struct sockaddr_un a; memset(&a, 0, sizeof a); a.sun_family = AF_UNIX;
    if (strlen(k->sockpath) >= sizeof a.sun_path) { free(k); return NULL; }
    strcpy(a.sun_path, k->sockpath);
    unlink(k->sockpath);
    if (bind(k->lfd, (struct sockaddr *)&a, sizeof a) < 0 || listen(k->lfd, 16) < 0) { LOG("bind %s: %s", k->sockpath, strerror(errno)); close(k->lfd); close(k->efd); free(k); return NULL; }
    chmod(k->sockpath, 0700);
    FILE *f = fopen(k->pidpath, "w"); if (f) { fprintf(f, "%d\n", (int)getpid()); fclose(f); }
    k->start_ms = now_ms();
    k->idle_ms = 2500;
    return k;
}

void kwl_run(Kwl *k) {
    struct pollfd *pf = NULL; int cap = 0;
    Client **cl = NULL; Reader **rl = NULL;
    while (!k->stop) {
        int n = 3;
        for (Client *c = k->clients; c; c = c->next) n++;
        for (Reader *r = k->readers; r; r = r->next) n++;
        if (n > cap) { cap = n * 2; pf = realloc(pf, cap * sizeof *pf); cl = realloc(cl, cap * sizeof *cl); rl = realloc(rl, cap * sizeof *rl); }
        int i = 0, ncl = 0, nrl = 0;
        pf[i].fd = k->efd; pf[i].events = POLLIN; i++;
        pf[i].fd = k->lfd; pf[i].events = POLLIN; i++;
        pf[i].fd = k->xwm ? k->xwm->fd : -1; pf[i].events = POLLIN; i++;
        for (Client *c = k->clients; c; c = c->next) { cl[ncl++] = c; pf[i].fd = c->fd; pf[i].events = POLLIN | (c->olen > c->osent ? POLLOUT : 0); i++; }
        for (Reader *r = k->readers; r; r = r->next) { rl[nrl++] = r; pf[i].fd = r->fd; pf[i].events = POLLIN; i++; }

        uint32_t now = now_ms();
        int timeout = -1;
        if (k->work) {
            uint32_t interval = 1000u / (uint32_t)k->fps, due = k->last_tick + interval;
            timeout = (int32_t)(due - now) > 0 ? (int)(due - now) : 0;
        }
        if (k->readers) timeout = timeout < 0 || timeout > 200 ? 200 : timeout;
        if (k->xwm_path[0] && !k->xwm) timeout = timeout < 0 || timeout > 150 ? 150 : timeout;
        if (k->idle_ms && k->had_window && k->nmapped == 0 && k->idle_since) {
            int rem = (int)(k->idle_ms - (int)(now - k->idle_since)); if (rem < 0) rem = 0;
            int gr = (int32_t)(k->grace_until - now); if (gr > rem) rem = gr;
            if (launch_recent(k) && rem < 1000) rem = 1000;
            if (timeout < 0 || rem < timeout) timeout = rem;
        } else if (k->idle_ms && !k->had_window && !k->clients) {
            int rem = (int)(90000 - (now - k->start_ms)); if (rem < 0) rem = 0;
            if (timeout < 0 || rem < timeout) timeout = rem;
        }
        int pr = poll(pf, (nfds_t)n, timeout);
        if (pr < 0 && errno != EINTR) break;
        now = now_ms();
        i = 0;
        if (pf[i].revents & POLLIN) drain_cmds(k);
        i++;
        int lrev = pf[i].revents; i++;
        int xrev = pf[i].revents; i++;
        if (lrev & POLLIN) {
            for (;;) {
                int fd = accept4(k->lfd, NULL, NULL, SOCK_CLOEXEC | SOCK_NONBLOCK);
                if (fd < 0) break;
                Client *c = calloc(1, sizeof *c); c->k = k; c->fd = fd; c->next = k->clients; k->clients = c;
                c_new(c, 1, I_DISPLAY, 1, NULL);
                read_app_tag(c);
                k->grace_until = now + 30000;   /* нова програма запускається: не виходити, поки вона не відкриє вікно */
                LOG("клієнт підключився pid=%d tag=%s%s", c->pid, c->app_tag, c->is_xwl ? " [Xwayland]" : "");
            }
        }
        if (k->xwm && (xrev & (POLLIN | POLLHUP | POLLERR))) xwm_read(k);
        if (k->xwm_path[0] && !k->xwm && (k->xwm_next_try == 0 || (int32_t)(now - k->xwm_next_try) >= 0)) {
            k->xwm_next_try = now + 150 ? now + 150 : 1;
            if (xwm_connect(k) == 0) k->xwm_deadline = 0;
            else if (k->xwm_deadline && (int32_t)(now - k->xwm_deadline) > 0) { LOG("xwm: X-сервер так і не з'явився, відмовляюсь"); k->xwm_path[0] = 0; }
        }
        /* клієнти: індекси стабільні, бо список не змінювався до цього місця */
        for (int ci = 0; ci < ncl; ci++, i++) {
            Client *c = cl[ci];
            if (pf[i].revents & (POLLERR | POLLHUP | POLLNVAL)) { if (!(pf[i].revents & POLLIN)) c->dead = 1; }
            if (pf[i].revents & POLLIN) client_read(c);
        }
        for (int ri = 0; ri < nrl; ri++, i++) {
            Reader *r = rl[ri];
            if (pf[i].revents & (POLLIN | POLLHUP)) { int done = 0; reader_step(k, r, &done); if (done) r->deadline = 0; }
        }
        /* прибрати завершені читачі */
        for (Reader **pp = &k->readers; *pp;) {
            Reader *r = *pp;
            if (r->deadline == 0 || (int32_t)(now - r->deadline) > 0) { *pp = r->next; close(r->fd); free(r->buf); free(r); }
            else pp = &r->next;
        }
        if (k->xwm) xwm_retry(k);
        if (k->work && (int32_t)(now - (k->last_tick + 1000u / (uint32_t)k->fps)) >= 0) do_tick(k);
        for (Client *c = k->clients; c; c = c->next) if (c->olen > c->osent) client_flush(c);
        for (Client **pp = &k->clients; *pp;) {
            Client *c = *pp;
            if (c->dead) { *pp = c->next; client_free(c); } else pp = &c->next;
        }
        if (k->idle_ms && k->had_window && k->nmapped == 0 && k->idle_since && now - k->idle_since >= (uint32_t)k->idle_ms && (int32_t)(now - k->grace_until) >= 0 && !launch_recent(k)) { LOG("простій, завершуюсь"); break; }
        if (k->idle_ms && !k->had_window && !k->clients && now - k->start_ms >= 90000) break;
    }
    free(pf); free(cl); free(rl);
    if (k->host.quit) k->host.quit(k->ud);
}

void kwl_destroy(Kwl *k) {
    if (k->xwm) xwm_close_conn(k);
    while (k->clients) { Client *c = k->clients; k->clients = c->next; client_free(c); }
    while (k->readers) { Reader *r = k->readers; k->readers = r->next; close(r->fd); free(r->buf); free(r); }
    unlink(k->sockpath); unlink(k->pidpath);
    close(k->lfd); close(k->efd);
    free(k->q); free(k->keymap); free(k->android_clip);
    free(k);
}
