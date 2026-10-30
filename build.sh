#!/data/data/com.termux/files/usr/bin/sh
# Збірка без Gradle: aapt2 -> ecj -> d8 -> zip -> apksigner
set -e

APP=KateLauncher0.7.2
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

echo "[1/5] Ресурси"
aapt2 compile --dir "$SRC/res" -o "$WORK/res.zip"

echo "[2/5] Лінкування"
aapt2 link -o "$WORK/base.apk" -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" -A "$SRC/assets" \
  --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  --version-code 10 --version-name 0.7.2 "$WORK/res.zip"

echo "[3/5] Java"
javac -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath "$ANDROID_JAR" -d "$WORK/classes" $(find "$SRC/src" -name "*.java")

echo "[4/5] DEX"
d8 --release --min-api $MIN_SDK --lib "$ANDROID_JAR" --output "$WORK" $(find "$WORK/classes" -name '*.class')

echo "[5/5] Пакування і підпис"
( cd "$WORK" && zip -q -j base.apk classes.dex )
if command -v zipalign >/dev/null 2>&1; then
  zipalign -f 4 "$WORK/base.apk" "$WORK/aligned.apk"
else
  cp "$WORK/base.apk" "$WORK/aligned.apk"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$OUT/$APP.apk" "$WORK/aligned.apk"

echo "Готово: $OUT/$APP.apk"
termux-open "$OUT/$APP.apk" 2>/dev/null || echo "Відкрийте APK вручну: $OUT/$APP.apk"
