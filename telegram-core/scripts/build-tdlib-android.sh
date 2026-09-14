#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PIN_FILE="$ROOT_DIR/TDLIB_VERSION"
WORK_DIR="${TMPDIR:-/tmp}/telegram-core-tdlib-build"
OUT_DIR="$ROOT_DIR/telegram-core-tdlib-android"

get_pin() { grep "^$1=" "$PIN_FILE" | cut -d= -f2-; }

TDLIB_COMMIT="$(get_pin TDLIB_COMMIT)"
NDK_VERSION="$(get_pin ANDROID_NDK_VERSION)"
ANDROID_PLATFORM="$(get_pin ANDROID_PLATFORM)"
CMAKE_VERSION="$(get_pin CMAKE_VERSION)"
OPENSSL_VERSION="$(get_pin OPENSSL_VERSION)"
TDLIB_INTERFACE="$(get_pin TDLIB_INTERFACE)"
ANDROID_STL="$(get_pin ANDROID_STL)"

: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT must point to the Android SDK}"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
git clone --filter=blob:none https://github.com/tdlib/td.git "$WORK_DIR/td"
git -C "$WORK_DIR/td" checkout --detach "$TDLIB_COMMIT"

cd "$WORK_DIR/td/example/android"

./build-openssl.sh "$ANDROID_SDK_ROOT" "$NDK_VERSION" "$WORK_DIR/openssl" "$OPENSSL_VERSION"
./build-tdlib.sh "$ANDROID_SDK_ROOT" "$NDK_VERSION" "$WORK_DIR/openssl" "$ANDROID_STL" "$TDLIB_INTERFACE"

rm -rf "$OUT_DIR/generated-jni-libs" "$OUT_DIR/generated-src"
mkdir -p "$OUT_DIR/generated-jni-libs" "$OUT_DIR/generated-src/main/java/org/drinkless/tdlib"

cp -a tdlib/libs/. "$OUT_DIR/generated-jni-libs/"
cp -f tdlib/java/org/drinkless/tdlib/JsonClient.java \
  "$OUT_DIR/generated-src/main/java/org/drinkless/tdlib/JsonClient.java"

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  test -s "$OUT_DIR/generated-jni-libs/$abi/libtdjsonjava.so"
done
test -s "$OUT_DIR/generated-src/main/java/org/drinkless/tdlib/JsonClient.java"

printf '%s\n' "$TDLIB_COMMIT" > "$OUT_DIR/generated-tdlib-commit.txt"
printf '%s\n' "$OPENSSL_VERSION" > "$OUT_DIR/generated-openssl-version.txt"
