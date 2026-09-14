#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ZIP_PATH="${1:-${TDLIB_ZIP:-}}"
if [ -z "$ZIP_PATH" ]; then
  echo "Usage: $0 /path/to/tdlib.zip" >&2
  exit 2
fi

MODULE="$ROOT_DIR/telegram-core-tdlib-android"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q "$ZIP_PATH" -d "$TMP"
test -f "$TMP/tdlib/java/org/drinkless/tdlib/JsonClient.java"

rm -rf "$MODULE/generated-src" "$MODULE/generated-jni-libs"
mkdir -p "$MODULE/generated-src/main/java" "$MODULE/generated-jni-libs"
cp -R "$TMP/tdlib/java/." "$MODULE/generated-src/main/java/"
cp -R "$TMP/tdlib/libs/." "$MODULE/generated-jni-libs/"

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  test -s "$MODULE/generated-jni-libs/$abi/libtdjsonjava.so"
done

echo "Imported TDLib JSONJava into $MODULE"
