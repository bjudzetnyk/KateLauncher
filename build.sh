#!/data/data/com.termux/files/usr/bin/sh
# Збірка без Gradle: aapt2 -> ecj -> d8 -> zip -> apksigner
set -e

APP=KateLauncher0.8.0ExLE
MIN_SDK=30
TARGET_SDK=30

SRC="$(cd "$(dirname "$0")" && pwd)"
WORK="$HOME/build/$APP"
OUT="$SRC/out"
ANDROID_JAR="${ANDROID_JAR:-$HOME/.android/android.jar}"
KEYSTORE="$HOME/.android/debug.keystore"

[ -f "$ANDROID_JAR" ] || { echo "Немає $ANDROID_JAR (потрібен android.jar, API 33+)"; exit 1; }

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkey -v -keystore "$KEYSTORE" -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass android -keypass android -dname "CN=Debug"
fi

rm -rf "$WORK"
mkdir -p "$WORK/classes" "$OUT"

# --- Native: Wayland-композитор (C). Без нього працюють усі функції, крім справжніх Linux-вікон. ---
case "$(uname -m)" in
  aarch64|arm64) ABI=arm64-v8a ;;
  armv7*|armv8l|arm*) ABI=armeabi-v7a ;;
  x86_64) ABI=x86_64 ;;
  i*86) ABI=x86 ;;
  *) ABI="" ;;
esac
HAVE_NATIVE=0
if [ -n "$ABI" ] && command -v clang >/dev/null 2>&1; then
  JNI_H="$(find "${PREFIX:-/usr}" -name jni.h 2>/dev/null | head -1)"
  if [ -n "$JNI_H" ]; then
    echo "[0/5] Native ($ABI)"
    mkdir -p "$WORK/lib/$ABI"
    JI="$(dirname "$JNI_H")"
    clang -shared -fPIC -O2 -Wall -I"$JI" $([ -f "$JI/linux/jni_md.h" ] && echo "-I$JI/linux") -I"$SRC/jni" \
      -o "$WORK/lib/$ABI/libkatewl.so" "$SRC/jni/kwl.c" "$SRC/jni/kwl_jni.c" -ldl \
      && HAVE_NATIVE=1 || echo "УВАГА: native-збірка не вдалась — Linux-вікна не працюватимуть"
  else
    echo "УВАГА: не знайдено jni.h (pkg install ndk-sysroot або openjdk) — Linux-вікна не працюватимуть"
  fi
else
  echo "УВАГА: немає clang (pkg install clang) — Linux-вікна не працюватимуть"
fi

# libkatewl.so лежить у APK без стиснення й вирівняна (extractNativeLibs=false), як у Termux:X11.
# Без zipalign так не можна — тоді просимо систему розпакувати бібліотеку.
MANIFEST="$SRC/AndroidManifest.xml"
if [ "$HAVE_NATIVE" = 1 ] && ! command -v zipalign >/dev/null 2>&1; then
  MANIFEST="$WORK/AndroidManifest.xml"
  sed 's/android:extractNativeLibs="false"/android:extractNativeLibs="true"/' "$SRC/AndroidManifest.xml" > "$MANIFEST"
fi

echo "[1/5] Ресурси"
aapt2 compile --dir "$SRC/res" -o "$WORK/res.zip"

echo "[2/5] Лінкування"
aapt2 link -o "$WORK/base.apk" -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" -A "$SRC/assets" \
  --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  --version-code 12 --version-name 0.8.0ExLE "$WORK/res.zip"

echo "[3/5] Java"
javac -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath "$ANDROID_JAR" -d "$WORK/classes" $(find "$SRC/src" -name "*.java")

echo "[4/5] DEX"
d8 --release --min-api $MIN_SDK --lib "$ANDROID_JAR" --output "$WORK" $(find "$WORK/classes" -name '*.class')

echo "[5/5] Пакування і підпис"
( cd "$WORK" && zip -q -j base.apk classes.dex )
if [ "$HAVE_NATIVE" = 1 ]; then
  if command -v zipalign >/dev/null 2>&1; then ( cd "$WORK" && zip -q -0 -X base.apk "lib/$ABI/libkatewl.so" ); else ( cd "$WORK" && zip -q -X base.apk "lib/$ABI/libkatewl.so" ); fi
fi
if command -v zipalign >/dev/null 2>&1; then
  zipalign -p -f 4 "$WORK/base.apk" "$WORK/aligned.apk"
else
  cp "$WORK/base.apk" "$WORK/aligned.apk"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$OUT/$APP.apk" "$WORK/aligned.apk"

echo "Готово: $OUT/$APP.apk"
termux-open "$OUT/$APP.apk" 2>/dev/null || echo "Відкрийте APK вручну: $OUT/$APP.apk"
