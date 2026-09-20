#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT_DIR/TDLIB_VERSION"

WORK_DIR="${WORK_DIR:-$ROOT_DIR/native/.work}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/native/out}"
TDLIB_REPOSITORY="${TDLIB_REPOSITORY:-https://github.com/tdlib/td.git}"
rm -rf "$WORK_DIR" "$OUT_DIR"
mkdir -p "$WORK_DIR" "$OUT_DIR"

git clone --filter=blob:none "$TDLIB_REPOSITORY" "$WORK_DIR/td"
git -C "$WORK_DIR/td" checkout --detach "$TDLIB_COMMIT"
ACTUAL="$(git -C "$WORK_DIR/td" rev-parse HEAD)"
test "$ACTUAL" = "$TDLIB_COMMIT"

cp "$WORK_DIR/td/example/android/Dockerfile" "$WORK_DIR/Dockerfile"
cp "$WORK_DIR/td/example/android/check-environment.sh" "$WORK_DIR/"
cp "$WORK_DIR/td/example/android/fetch-sdk.sh" "$WORK_DIR/"
cp "$WORK_DIR/td/example/android/build-openssl.sh" "$WORK_DIR/"
cp "$WORK_DIR/td/example/android/build-tdlib.sh" "$WORK_DIR/"

docker build \
  --platform linux/amd64 \
  --build-arg COMMIT_HASH="$TDLIB_COMMIT" \
  --build-arg ANDROID_NDK_VERSION="$ANDROID_NDK_VERSION" \
  --build-arg OPENSSL_VERSION="$OPENSSL_VERSION" \
  --build-arg ANDROID_STL="$ANDROID_STL" \
  --build-arg TDLIB_INTERFACE="$TDLIB_INTERFACE" \
  -f "$WORK_DIR/Dockerfile" \
  --output "$OUT_DIR" \
  "$WORK_DIR/td/example/android"

find "$OUT_DIR" -maxdepth 2 -type f -print
