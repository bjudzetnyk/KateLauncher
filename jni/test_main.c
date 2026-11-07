/* Тестовий хост ядра на Linux. Читає команди зі stdin, логує події, зберігає кадри в PPM.
 *   gcc -O1 -g jni/kwl.c jni/test_main.c -o kwltest -lpthread
 *   ./kwltest <dir> <keymap.xkb>        (змінні: KWL_DEBUG=1, OUTW/OUTH — розмір виводу)
 * Команди (по одній у рядку):
 *   resync | debug 0|1 | focus ID | move ID X Y | down ID X Y [BTN] | up ID X Y [BTN] | click ID X Y [BTN]
 *   key CODE [0|1] | tap CODE | cfg ID W H FLAGS | close ID | vis ID 0|1 | dismiss
 *   xwm PATH | dump ID | sleep MS | clip TEXT | out W H SCALE | quit */
#define _GNU_SOURCE
#include "kwl.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/mman.h>

static Kwl *K;
static struct { int id, w, h; uint32_t *p; } W[64];
static int nw;
static int autofocus;

static void on_add(void *u, int id, int kind, int parent, int px, int py, int w, int h, int sc, const char *tag, const char *app, const char *title, int deco) {
    printf("ADD id=%d kind=%d parent=%d pos=%d,%d %dx%d sc=%d tag='%s' app='%s' title='%s' deco=%d\n", id, kind, parent, px, py, w, h, sc, tag, app, title, deco);
    if (nw < 64) { W[nw].id = id; W[nw].p = NULL; nw++; }
    fflush(stdout);
    if (autofocus && kind == 0) kwl_cmd(K, KWL_CMD_FOCUS, id, 0, 0, 0, 0, 0, NULL);
}
static void on_buf(void *u, int id, int gen, int w, int h, int sc, int fd, int size) {
    printf("BUF id=%d gen=%d %dx%d sc=%d size=%d\n", id, gen, w, h, sc, size);
    for (int i = 0; i < nw; i++) if (W[i].id == id) { W[i].p = mmap(NULL, size, PROT_READ, MAP_SHARED, fd, 0); W[i].w = w; W[i].h = h; }
    fflush(stdout);
}
static void dump(int id) {
    for (int i = 0; i < nw; i++) if (W[i].id == id && W[i].p) {
        char n[64]; snprintf(n, sizeof n, "/tmp/win%d.ppm", id); FILE *f = fopen(n, "wb");
        fprintf(f, "P6\n%d %d\n255\n", W[i].w, W[i].h);
        for (int j = 0; j < W[i].w * W[i].h; j++) { uint32_t p = W[i].p[j]; fputc(p & 255, f); fputc((p >> 8) & 255, f); fputc((p >> 16) & 255, f); }
        fclose(f); printf("DUMPED %s\n", n); fflush(stdout);
    }
}
static int ndmg;
static void on_dmg(void *u, int id, int gen, int x, int y, int w, int h) { if (ndmg++ < 8) { printf("DMG id=%d gen=%d %d,%d %dx%d\n", id, gen, x, y, w, h); fflush(stdout); } }
static void on_title(void *u, int id, const char *t) { printf("TITLE id=%d '%s'\n", id, t); fflush(stdout); }
static void on_appid(void *u, int id, const char *t) { printf("APPID id=%d '%s'\n", id, t); fflush(stdout); }
static void on_hints(void *u, int id, int a, int b, int c, int d) { printf("HINTS id=%d %d %d %d %d\n", id, a, b, c, d); fflush(stdout); }
static void on_rem(void *u, int id) { printf("REMOVE id=%d\n", id); fflush(stdout); }
static void on_req(void *u, int id, int k, int a) { printf("REQ id=%d kind=%d arg=%d\n", id, k, a); fflush(stdout); }
static void on_pop(void *u, int id, int x, int y, int w, int h) { printf("POPUP id=%d %d,%d %dx%d\n", id, x, y, w, h); fflush(stdout); }
static void on_clip(void *u, const char *t) { printf("CLIP '%s'\n", t); fflush(stdout); }
static void on_quit(void *u) { printf("QUIT\n"); fflush(stdout); }

static void *reader(void *a) {
    char line[1024];
    while (fgets(line, sizeof line, stdin)) {
        line[strcspn(line, "\r\n")] = 0;
        char op[32] = ""; int a1 = 0, a2 = 0, a3 = 0, a4 = 0; char s[900] = "";
        int n = sscanf(line, "%31s %d %d %d %d", op, &a1, &a2, &a3, &a4);
        if (n < 1) continue;
        if (!strcmp(op, "focus")) kwl_cmd(K, KWL_CMD_FOCUS, a1, 0, 0, 0, 0, 0, NULL);
        else if (!strcmp(op, "move")) kwl_cmd(K, KWL_CMD_POINTER_MOVE, a1, 0, 0, 0, a2, a3, NULL);
        else if (!strcmp(op, "down")) kwl_cmd(K, KWL_CMD_POINTER_BUTTON, a1, n >= 5 ? a4 : 0x110, 1, 0, a2, a3, NULL);
        else if (!strcmp(op, "up")) kwl_cmd(K, KWL_CMD_POINTER_BUTTON, a1, n >= 5 ? a4 : 0x110, 0, 0, a2, a3, NULL);
        else if (!strcmp(op, "click")) {
            int b = n >= 5 ? a4 : 0x110;
            kwl_cmd(K, KWL_CMD_POINTER_MOVE, a1, 0, 0, 0, a2, a3, NULL); usleep(30000);
            kwl_cmd(K, KWL_CMD_POINTER_BUTTON, a1, b, 1, 0, a2, a3, NULL); usleep(60000);
            kwl_cmd(K, KWL_CMD_POINTER_BUTTON, a1, b, 0, 0, a2, a3, NULL);
        }
        else if (!strcmp(op, "scroll")) kwl_cmd(K, KWL_CMD_POINTER_AXIS, a1, 0, a2, 0, a3, 0, NULL);
        else if (!strcmp(op, "key")) kwl_cmd(K, KWL_CMD_KEY, a1, n >= 3 ? a2 : 1, 0, 0, 0, 0, NULL);
        else if (!strcmp(op, "tap")) { kwl_cmd(K, KWL_CMD_KEY, a1, 1, 0, 0, 0, 0, NULL); usleep(40000); kwl_cmd(K, KWL_CMD_KEY, a1, 0, 0, 0, 0, 0, NULL); }
        else if (!strcmp(op, "cfg")) kwl_cmd(K, KWL_CMD_CONFIGURE, a1, a2, a3, a4, 0, 0, NULL);
        else if (!strcmp(op, "close")) kwl_cmd(K, KWL_CMD_CLOSE, a1, 0, 0, 0, 0, 0, NULL);
        else if (!strcmp(op, "vis")) kwl_cmd(K, KWL_CMD_VISIBLE, a1, a2, 0, 0, 0, 0, NULL);
        else if (!strcmp(op, "dismiss")) kwl_cmd(K, KWL_CMD_DISMISS_POPUPS, 0, 0, 0, 0, 0, 0, NULL);
        else if (!strcmp(op, "out")) kwl_cmd(K, KWL_CMD_OUTPUT, a1, a2, a3, 0, 0, 0, NULL);
        else if (!strcmp(op, "xwm") && sscanf(line, "%*s %899s", s) == 1) kwl_cmd(K, KWL_CMD_XWM, 0, 0, 0, 0, 0, 0, s);
        else if (!strcmp(op, "clip")) kwl_cmd(K, KWL_CMD_CLIPBOARD, 0, 0, 0, 0, 0, 0, line + 5);
        else if (!strcmp(op, "resync")) kwl_cmd(K, KWL_CMD_RESYNC, 0, 0, 0, 0, 0, 0, NULL);
        else if (!strcmp(op, "debug")) kwl_cmd(K, KWL_CMD_DEBUG, a1, 0, 0, 0, 0, 0, NULL);
        else if (!strcmp(op, "dump")) dump(a1);
        else if (!strcmp(op, "autofocus")) autofocus = a1;
        else if (!strcmp(op, "sleep")) usleep((useconds_t)a1 * 1000);
        else if (!strcmp(op, "echo")) { printf("-- %s\n", line + 5); fflush(stdout); }
        else if (!strcmp(op, "quit")) { kwl_cmd(K, KWL_CMD_STOP, 0, 0, 0, 0, 0, 0, NULL); break; }
        else printf("?? %s\n", line);
        fflush(stdout);
    }
    kwl_cmd(K, KWL_CMD_STOP, 0, 0, 0, 0, 0, 0, NULL);
    return NULL;
}

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: %s dir keymap\n", argv[0]); return 2; }
    KwlHost h = { on_add, on_buf, on_dmg, on_title, on_appid, on_hints, on_rem, on_req, on_pop, on_clip, on_quit };
    FILE *f = fopen(argv[2], "r"); static char km[1 << 20]; size_t n = fread(km, 1, sizeof km - 1, f); km[n] = 0; fclose(f);
    int ow = getenv("OUTW") ? atoi(getenv("OUTW")) : 800, oh = getenv("OUTH") ? atoi(getenv("OUTH")) : 600;
    if (getenv("FOCUS_ON_ADD")) autofocus = 1;
    K = kwl_create(&h, NULL, argv[1], "wayland-kate", km, ow, oh, 1, 30);
    if (!K) { fprintf(stderr, "kwl_create failed\n"); return 1; }
    pthread_t t; pthread_create(&t, NULL, reader, NULL); pthread_detach(t);
    kwl_run(K);
    kwl_destroy(K);
    return 0;
}
