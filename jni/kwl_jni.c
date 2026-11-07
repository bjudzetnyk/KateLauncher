/* JNI-обгортка. Один .so використовується у двох процесах:
 *  - у процесі Termux (app_process): nativeStart/nativeCmd — композитор;
 *  - у лаунчері: nativeMap/nativeUnmap/nativeBlit — копіювання змінених областей у Bitmap. */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <dlfcn.h>
#include <pthread.h>
#include <sys/mman.h>
#include "kwl.h"

#define CLS "com/katelauncher/bjudzetnykukrainianlinuxcommunity/linux/wl/Native"
#define HCLS "com/katelauncher/bjudzetnykukrainianlinuxcommunity/linux/wl/HostMain"

static JavaVM *g_vm;
static jclass g_host;
static jmethodID m_add, m_buf, m_dmg, m_title, m_appid, m_hints, m_rem, m_req, m_pop, m_clip, m_quit;
static Kwl *g_k;
static pthread_t g_thr;

static JNIEnv *env_(void) {
    JNIEnv *e = NULL;
    if ((*g_vm)->GetEnv(g_vm, (void **)&e, JNI_VERSION_1_6) == JNI_OK) return e;
    (*g_vm)->AttachCurrentThread(g_vm, (void *)&e, NULL);
    return e;
}

jint JNI_OnLoad(JavaVM *vm, void *r) { (void)r; g_vm = vm; return JNI_VERSION_1_6; }

static jstring js(JNIEnv *e, const char *s) { return (*e)->NewStringUTF(e, s ? s : ""); }

static void h_add(void *u, int id, int kind, int parent, int px, int py, int w, int h, int sc, const char *tag, const char *app, const char *title, int deco) {
    JNIEnv *e = env_(); (void)u;
    jstring a = js(e, tag), b = js(e, app), c = js(e, title);
    (*e)->CallStaticVoidMethod(e, g_host, m_add, id, kind, parent, px, py, w, h, sc, a, b, c, (jboolean)(deco != 0));
    (*e)->DeleteLocalRef(e, a); (*e)->DeleteLocalRef(e, b); (*e)->DeleteLocalRef(e, c);
    if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
}
static void h_buf(void *u, int id, int gen, int w, int h, int sc, int fd, int size) {
    JNIEnv *e = env_(); (void)u;
    int d = dup(fd);
    (*e)->CallStaticVoidMethod(e, g_host, m_buf, id, gen, w, h, sc, d, size);
    if ((*e)->ExceptionCheck(e)) { (*e)->ExceptionClear(e); close(d); }
}
static void h_dmg(void *u, int id, int gen, int x, int y, int w, int h) {
    JNIEnv *e = env_(); (void)u;
    (*e)->CallStaticVoidMethod(e, g_host, m_dmg, id, gen, x, y, w, h);
    if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
}
static void h_str(JNIEnv *e, jmethodID m, int id, const char *s) {
    jstring a = js(e, s);
    (*e)->CallStaticVoidMethod(e, g_host, m, id, a);
    (*e)->DeleteLocalRef(e, a);
    if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
}
static void h_title(void *u, int id, const char *t) { (void)u; h_str(env_(), m_title, id, t); }
static void h_appid(void *u, int id, const char *t) { (void)u; h_str(env_(), m_appid, id, t); }
static void h_hints(void *u, int id, int a, int b, int c, int d) {
    JNIEnv *e = env_(); (void)u;
    (*e)->CallStaticVoidMethod(e, g_host, m_hints, id, a, b, c, d);
    if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
}
static void h_rem(void *u, int id) { JNIEnv *e = env_(); (void)u; (*e)->CallStaticVoidMethod(e, g_host, m_rem, id); if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e); }
static void h_req(void *u, int id, int k, int a) { JNIEnv *e = env_(); (void)u; (*e)->CallStaticVoidMethod(e, g_host, m_req, id, k, a); if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e); }
static void h_pop(void *u, int id, int x, int y, int w, int h) { JNIEnv *e = env_(); (void)u; (*e)->CallStaticVoidMethod(e, g_host, m_pop, id, x, y, w, h); if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e); }
static void h_clip(void *u, const char *t) {
    JNIEnv *e = env_(); (void)u; jstring a = js(e, t);
    (*e)->CallStaticVoidMethod(e, g_host, m_clip, a); (*e)->DeleteLocalRef(e, a);
    if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
}
static void h_quit(void *u) { JNIEnv *e = env_(); (void)u; (*e)->CallStaticVoidMethod(e, g_host, m_quit); if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e); }

static void *run_thread(void *a) { (void)a; kwl_run(g_k); return NULL; }

JNIEXPORT jboolean JNICALL Java_com_katelauncher_bjudzetnykukrainianlinuxcommunity_linux_wl_Native_nativeStart(
        JNIEnv *e, jclass c, jstring dir, jstring sock, jstring keymap, jint w, jint h, jint scale, jint fps) {
    (void)c;
    if (g_k) return JNI_FALSE;
    jclass hc = (*e)->FindClass(e, HCLS);
    if (!hc) { (*e)->ExceptionClear(e); return JNI_FALSE; }
    g_host = (jclass)(*e)->NewGlobalRef(e, hc);
    m_add = (*e)->GetStaticMethodID(e, g_host, "cbWindowAdd", "(IIIIIIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V");
    m_buf = (*e)->GetStaticMethodID(e, g_host, "cbBuffer", "(IIIIIII)V");
    m_dmg = (*e)->GetStaticMethodID(e, g_host, "cbDamage", "(IIIIII)V");
    m_title = (*e)->GetStaticMethodID(e, g_host, "cbTitle", "(ILjava/lang/String;)V");
    m_appid = (*e)->GetStaticMethodID(e, g_host, "cbAppId", "(ILjava/lang/String;)V");
    m_hints = (*e)->GetStaticMethodID(e, g_host, "cbHints", "(IIIII)V");
    m_rem = (*e)->GetStaticMethodID(e, g_host, "cbRemove", "(I)V");
    m_req = (*e)->GetStaticMethodID(e, g_host, "cbRequest", "(III)V");
    m_pop = (*e)->GetStaticMethodID(e, g_host, "cbPopup", "(IIIII)V");
    m_clip = (*e)->GetStaticMethodID(e, g_host, "cbClipboard", "(Ljava/lang/String;)V");
    m_quit = (*e)->GetStaticMethodID(e, g_host, "cbQuit", "()V");
    if ((*e)->ExceptionCheck(e)) { (*e)->ExceptionClear(e); return JNI_FALSE; }
    KwlHost host = { h_add, h_buf, h_dmg, h_title, h_appid, h_hints, h_rem, h_req, h_pop, h_clip, h_quit };
    const char *d = (*e)->GetStringUTFChars(e, dir, NULL), *s = (*e)->GetStringUTFChars(e, sock, NULL), *k = (*e)->GetStringUTFChars(e, keymap, NULL);
    g_k = kwl_create(&host, NULL, d, s, k, w, h, scale, fps);
    (*e)->ReleaseStringUTFChars(e, dir, d); (*e)->ReleaseStringUTFChars(e, sock, s); (*e)->ReleaseStringUTFChars(e, keymap, k);
    if (!g_k) return JNI_FALSE;
    pthread_create(&g_thr, NULL, run_thread, NULL);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_katelauncher_bjudzetnykukrainianlinuxcommunity_linux_wl_Native_nativeCmd(
        JNIEnv *e, jclass c, jint op, jint a, jint b, jint cc, jint d, jdouble x, jdouble y, jstring s) {
    (void)c;
    if (!g_k) return;
    if (s) { const char *t = (*e)->GetStringUTFChars(e, s, NULL); kwl_cmd(g_k, op, a, b, cc, d, x, y, t); (*e)->ReleaseStringUTFChars(e, s, t); }
    else kwl_cmd(g_k, op, a, b, cc, d, x, y, NULL);
}

/* ---- Сторона лаунчера: відображення області й копіювання у Bitmap ---- */

JNIEXPORT jlong JNICALL Java_com_katelauncher_bjudzetnykukrainianlinuxcommunity_linux_wl_Native_nativeMap(JNIEnv *e, jclass c, jint fd, jint size) {
    (void)e; (void)c;
    void *m = mmap(NULL, (size_t)size, PROT_READ, MAP_SHARED, fd, 0);
    return m == MAP_FAILED ? 0 : (jlong)(intptr_t)m;
}

JNIEXPORT void JNICALL Java_com_katelauncher_bjudzetnykukrainianlinuxcommunity_linux_wl_Native_nativeUnmap(JNIEnv *e, jclass c, jlong p, jint size) {
    (void)e; (void)c;
    if (p) munmap((void *)(intptr_t)p, (size_t)size);
}

typedef struct { uint32_t width, height, stride, format, flags; } BmInfo;   /* AndroidBitmapInfo */
typedef int (*FnInfo)(JNIEnv *, jobject, BmInfo *);
typedef int (*FnLock)(JNIEnv *, jobject, void **);
typedef int (*FnUnlock)(JNIEnv *, jobject);
static FnInfo f_info; static FnLock f_lock; static FnUnlock f_unlock;

static int bm_init(void) {
    if (f_lock) return 1;
    void *h = dlopen("libjnigraphics.so", RTLD_NOW);
    if (!h) return 0;
    f_info = (FnInfo)dlsym(h, "AndroidBitmap_getInfo");
    f_lock = (FnLock)dlsym(h, "AndroidBitmap_lockPixels");
    f_unlock = (FnUnlock)dlsym(h, "AndroidBitmap_unlockPixels");
    return f_info && f_lock && f_unlock;
}

/* src: RGBA premultiplied, stride srcStridePx пікселів. Копіює прямокутник (sx,sy,w,h) у (dx,dy) растру. */
JNIEXPORT jboolean JNICALL Java_com_katelauncher_bjudzetnykukrainianlinuxcommunity_linux_wl_Native_nativeBlit(
        JNIEnv *e, jclass c, jlong src, jint srcStridePx, jint sx, jint sy, jint w, jint h, jobject bmp, jint dx, jint dy) {
    (void)c;
    if (!src || !bm_init()) return JNI_FALSE;
    BmInfo info; void *px;
    if (f_info(e, bmp, &info) != 0 || info.format != 1 /* RGBA_8888 */) return JNI_FALSE;
    if (dx < 0 || dy < 0 || w <= 0 || h <= 0) return JNI_FALSE;
    if ((uint32_t)(dx + w) > info.width) w = (int)info.width - dx;
    if ((uint32_t)(dy + h) > info.height) h = (int)info.height - dy;
    if (w <= 0 || h <= 0) return JNI_FALSE;
    if (f_lock(e, bmp, &px) != 0) return JNI_FALSE;
    const uint32_t *s = (const uint32_t *)(intptr_t)src + (size_t)sy * srcStridePx + sx;
    uint8_t *d = (uint8_t *)px + (size_t)dy * info.stride + (size_t)dx * 4;
    for (int y = 0; y < h; y++) { memcpy(d, s, (size_t)w * 4); s += srcStridePx; d += info.stride; }
    f_unlock(e, bmp);
    return JNI_TRUE;
}
