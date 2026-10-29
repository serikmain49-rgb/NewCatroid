#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK containing API 34 and build-tools 34.0.0}"
: "${GOOSE_RESULTS:?Set GOOSE_RESULTS to the directory containing the two NDK build outputs}"
root=$(cd "$(dirname "$0")" && pwd)
out=${APK_OUT_DIR:-"$root/out"}
build_tools="$ANDROID_HOME/build-tools/34.0.0"
android_jar="$ANDROID_HOME/platforms/android-34/android.jar"
mkdir -p "$out/classes" "$out/dex"
"$build_tools/aapt2" link -I "$android_jar" --manifest "$root/AndroidManifest.xml" -o "$out/base.apk"
javac --release 8 -classpath "$android_jar" -d "$out/classes" "$root"/src/org/catrobat/catroid/goosepoc/*.java "$root"/../tests/MovementBatchTest.java
"$build_tools/d8" --min-api 26 --lib "$android_jar" --output "$out/dex" "$out/classes"/org/catrobat/catroid/goosepoc/*.class
python3 - "$out" "$GOOSE_RESULTS" <<'PY'
import pathlib, shutil, sys, zipfile
out, results = map(pathlib.Path, sys.argv[1:])
shutil.copyfile(out / 'base.apk', out / 'unsigned-unaligned.apk')
with zipfile.ZipFile(out / 'unsigned-unaligned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
    apk.write(out / 'dex/classes.dex', 'classes.dex')
    for abi, target in [('arm64-v8a', 'aarch64-linux-android26'), ('armeabi-v7a', 'armv7a-linux-androideabi26')]:
        apk.write(results / target / 'libgoose_poc.so', f'lib/{abi}/libgoose_poc.so')
PY
"$build_tools/zipalign" -f -p 4 "$out/unsigned-unaligned.apk" "$out/unsigned.apk"
if [[ ! -f "$out/debug.keystore" ]]; then
    keytool -genkeypair -keystore "$out/debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Goose PoC Debug' -keyalg RSA -keysize 2048 -validity 3650 >/dev/null 2>&1
fi
"$build_tools/apksigner" sign --ks "$out/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out/goose-jni-test.apk" "$out/unsigned.apk"
"$build_tools/apksigner" verify "$out/goose-jni-test.apk"
