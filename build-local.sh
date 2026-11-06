#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/tmp/zhixing-android/sdk}}"
JAVA_HOME="${JAVA_HOME:-/tmp/zhixing-android/jdk/amazon-corretto-17.jdk/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  echo "需要 JDK 17：请设置 JAVA_HOME" >&2
  exit 1
fi
if [[ -n "${ANDROID_BUILD_TOOLS:-}" ]]; then
  BT="$ANDROID_BUILD_TOOLS"
elif [[ -d "$SDK/build-tools/34.0.0" ]]; then
  BT="$SDK/build-tools/34.0.0"
else
  BT="$SDK/build-tools/android-14"
fi
if [[ -n "${ANDROID_JAR:-}" ]]; then
  PLATFORM="$ANDROID_JAR"
elif [[ -f "$SDK/platforms/android-33/android.jar" ]]; then
  PLATFORM="$SDK/platforms/android-33/android.jar"
else
  PLATFORM="$SDK/platforms/android-13/android.jar"
fi
for dependency in "$BT/aapt2" "$BT/zipalign" "$BT/apksigner" "$BT/lib/d8.jar" "$PLATFORM"; do
  if [[ ! -e "$dependency" ]]; then
    echo "缺少 Android SDK 组件：$dependency" >&2
    exit 1
  fi
done
TMP="$(mktemp -d /tmp/zhixing-build.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes" "$TMP/dex" "$TMP/gen" "$ROOT/build"
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$TMP/res.zip"
"$BT/aapt2" link -o "$TMP/base.apk" -I "$PLATFORM" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  -A "$ROOT/app/src/main/assets" --java "$TMP/gen" \
  --min-sdk-version 21 --target-sdk-version 28 \
  --version-code 7 --version-name 1.0.0 "$TMP/res.zip"
find "$ROOT/app/src/main/java" "$TMP/gen" -name '*.java' > "$TMP/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$PLATFORM" -d "$TMP/classes" @"$TMP/sources.txt"
find "$TMP/classes" -name '*.class' > "$TMP/classes.txt"
"$JAVA_HOME/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLATFORM" --min-api 21 --output "$TMP/dex" @"$TMP/classes.txt"
(cd "$TMP" && zip -q -j base.apk dex/classes.dex)
"$BT/zipalign" -f 4 "$TMP/base.apk" "$TMP/aligned.apk"
KS="${SIGNING_KEYSTORE:-$ROOT/build/zhixing-release.keystore}"
SIGNING_PASSWORD="${SIGNING_PASSWORD:-zhixing-tv-local}"
SIGNING_ALIAS="${SIGNING_ALIAS:-zhixing}"
if [[ ! -f "$KS" ]]; then
  if [[ -n "${SIGNING_KEYSTORE:-}" ]]; then
    echo "签名密钥不存在：$KS" >&2
    exit 1
  fi
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass "$SIGNING_PASSWORD" \
    -alias "$SIGNING_ALIAS" -keypass "$SIGNING_PASSWORD" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Zhixing TV, O=Local"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$SIGNING_PASSWORD" \
  --ks-key-alias "$SIGNING_ALIAS" --key-pass "pass:$SIGNING_PASSWORD" \
  --out "$ROOT/知行答题-TV.apk" "$TMP/aligned.apk"
"$BT/apksigner" verify --verbose "$ROOT/知行答题-TV.apk"
echo "APK: $ROOT/知行答题-TV.apk"
