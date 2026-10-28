#!/usr/bin/env bash
set -euo pipefail
: "${GOOSE:?Set GOOSE to the Goose compiler executable}"
: "${CC:?Set CC to a C compiler; for Android use the NDK target clang}"
root=$(cd "$(dirname "$0")" && pwd)
out=${OUT_DIR:-"$root/out"}
mkdir -p "$out"
"$GOOSE" --standalone --header "$out/goose_poc.h" -o "$out/math.c" "$root/math.goose"
"$CC" -std=c11 -O2 -I "$out" "$out/math.c" "$root/smoke.c" -o "$out/smoke" -lm -ldl
# JNI headers are provided by the NDK compiler sysroot. Android only.
if [[ ${BUILD_JNI:-0} == 1 ]]; then
    "$CC" -std=c11 -O2 -fPIC -shared -I "$out" "$out/math.c" "$root/jni.c" -o "$out/libgoose_poc.so" -lm -ldl
fi
