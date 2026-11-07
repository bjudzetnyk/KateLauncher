#!/bin/bash
# Автотест X11-режиму на звичайному Linux-ПК: композитор + справжній Xwayland + xterm/xev.
# Потрібно: gcc, javac, xwayland, xterm, x11-apps (xev), wmctrl.   Ubuntu: apt install gcc default-jdk-headless xwayland xterm x11-apps wmctrl
# Запуск:  sh jni/tests/run_xwm_test.sh        (код виходу 0 — усе гаразд)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"; SRC="$HERE/../.."; W="$(mktemp -d /tmp/kwl-test.XXXXXX)"; D="$W/rt"; X=7
fail=0; ok() { echo "  ok   $1"; }; bad() { echo "  FAIL $1"; fail=1; }
check() { if grep -q -- "$2" "$3"; then ok "$1"; else bad "$1  (немає '$2' у $3)"; fi; }
gcc -O1 -g -Wall -Wno-misleading-indentation -fsanitize=address,undefined "$SRC/jni/kwl.c" "$SRC/jni/test_main.c" -o "$W/kwltest" -lpthread || exit 2
javac -d "$W/cls" "$SRC/src/com/katelauncher/bjudzetnykukrainianlinuxcommunity/linux/wl/KeyTable.java" || exit 2
java -cp "$W/cls" com.katelauncher.bjudzetnykukrainianlinuxcommunity.linux.wl.KeyTable "$W/k.xkb" || exit 2
mkdir -p "$D"; chmod 700 "$D"; mkfifo "$W/fifo"
rm -f /tmp/.X11-unix/X$X /tmp/.X$X-lock
( KWL_DEBUG=1 FOCUS_ON_ADD=1 "$W/kwltest" "$D" "$W/k.xkb" < "$W/fifo" > "$W/out" 2> "$W/err" ) & KP=$!
exec 7>"$W/fifo"; sleep 0.7
( XDG_RUNTIME_DIR="$D" WAYLAND_DISPLAY=wayland-kate KATE_XWL=1 Xwayland :$X -rootless -ac -shm -noreset -nolisten tcp > "$W/xw.out" 2>&1 ) & XP=$!
sleep 1.5; echo "xwm /tmp/.X11-unix/X$X" >&7; sleep 1
[ -e "$D/xwm.ready" ] && ok "XWM підключився (xwm.ready)" || bad "XWM не підключився"
( DISPLAY=:$X xev -geometry 300x200 > "$W/xev.out" 2>&1 ) & sleep 1.5
echo "click 1 50 60" >&7; echo "tap 30" >&7; sleep 0.6
( DISPLAY=:$X xterm -geometry 40x10 -e sh -c 'cat > '"$W"'/term_in.txt' > /dev/null 2>&1 ) & sleep 2
echo "tap 30" >&7; echo "tap 48" >&7; echo "tap 28" >&7; sleep 0.5      # a b Enter -> xterm
echo "key 29 1" >&7; echo "click 2 60 60" >&7; echo "key 29 0" >&7; sleep 1  # Ctrl+клік: меню xterm (override-redirect)
echo "focus 1" >&7; echo "click 1 120 90" >&7; sleep 0.6
echo "cfg 2 500 300 0" >&7; sleep 0.8
DISPLAY=:$X wmctrl -i -r "$(DISPLAY=:$X wmctrl -l | awk '{print $1}' | tail -1)" -b add,fullscreen 2>/dev/null; sleep 0.8
echo "close 1" >&7; sleep 1; echo "close 2" >&7; sleep 1
echo "quit" >&7; sleep 1; exec 7>&-
kill $XP 2>/dev/null; pkill -x xterm 2>/dev/null; pkill -x xev 2>/dev/null; wait $KP 2>/dev/null
echo "--- перевірки"
check "вікно X11 з'явилось у композиторі" "ADD id=1 kind=0" "$W/out"
check "клас вікна (WM_CLASS) передано" "app='XTerm'\|app='xev'" "$W/out"
check "popup X11 має kind=2" "kind=2" "$W/out"
check "кліки дійшли до програми X11 (xev)" "ButtonPress" "$W/xev.out"
check "клавіші дійшли до xterm" "^ab" "$W/term_in.txt"
check "зміна розміру від лаунчера застосована" "500x300" "$W/out"
check "запит повноекранного режиму від програми" "REQ id=.* kind=5" "$W/out"
check "вікна прибрано при закритті" "REMOVE id=1" "$W/out"
if grep -q "AddressSanitizer\|runtime error\|LeakSanitizer" "$W/err"; then bad "санітайзер знайшов помилки (див. $W/err)"; else ok "санітайзер: чисто"; fi
[ $fail = 0 ] && { echo "УСЕ ГАРАЗД"; rm -rf "$W"; } || echo "Журнали збережено у $W"
exit $fail
