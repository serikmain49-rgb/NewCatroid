# Goose experiment (not integrated into NewCatroid)

This isolated experiment exports an integer operation from Goose to C and builds
an Android JNI library plus a command-line smoke test. It does not replace an
engine component, package an APK, establish performance, or establish project
compatibility. No Android device run has been recorded.

The workflow pins Goose to cce94b919d27458856ae2685974049eac78debcf and
cross-compiles for API 26 ARM64 and ARMv7 with NDK 27.2.12479018. The generated
runtime may expose Android/Bionic or 32-bit address-space problems; a successful
host test cannot establish that either target works. Workflow failures must be
investigated, not hidden.

On an ARM64 Android device with adb, after obtaining a successful artifact:

```sh
adb push smoke /data/local/tmp/goose-smoke
adb shell chmod 755 /data/local/tmp/goose-smoke
adb shell /data/local/tmp/goose-smoke
```

To test JNI, a separate Android test application must declare the matching
`org.catrobat.catroid.goosepoc.GooseProbe.add(int, int)` native method, load
`libgoose_poc.so`, call it on one dedicated, long-lived thread, and verify the
return value. The JNI wrapper rejects calls from a different thread; it does not
manage thread lifetime or make Goose generally thread-safe. No app or APK uses
this code yet. Beware signed overflow: the smoke test uses small values only.

Goose reserves large virtual data stacks by default. This requires measurement
and likely configuration for 32-bit Android before any production integration.

Host verification: the pinned compiler was built on Linux x86-64 with GCC 13;
`build.sh` generated C, compiled it with GCC and the executable passed both
small integer test cases. The generated public header used `gs_i32` without
a definition, so the build script replaces that token with `int32_t` for this
one scalar-only experiment. This workaround must be revisited if the exported
ABI changes; it is not a general Goose header repair.

Cross-compilation verification (2026-10-29): Android NDK r27c, API 26,
compiled the generated C and JNI wrapper for both `aarch64-linux-android26`
and `armv7a-linux-androideabi26`. `file` identified the resulting executables
and shared objects as AArch64 and ARM EABI5 respectively; `llvm-readelf`
confirmed the ARM64 JNI export and Android libc dependencies. Neither binary
has been executed on Android, and the ARMv7 runtime's large default stack
reservation remains unverified.

## Standalone Android test APK

`android/build-apk.sh` builds a small separately installed test app (Android 8+
API 26+, ARM64 or ARMv7), using SDK platform 34, build-tools 34.0.0, JDK 17,
and the two cross-compiled JNI libraries. It does not contain NewCatroid. The
screen checks two Goose additions on the UI thread, then checks that a JNI call
from another thread is rejected. It requires no network or permissions.

To build, set `ANDROID_HOME`, set `GOOSE_RESULTS` to a directory containing
`aarch64-linux-android26/libgoose_poc.so` and
`armv7a-linux-androideabi26/libgoose_poc.so`, and run
`android/build-apk.sh`. The script generates a local debug signing key; such
keys differ across builds and are not intended for production releases.

On 2026-10-29 the standalone APK was built and its v2/v3 signatures verified;
its two JNI libraries were inspected in the archive. No Android device was
connected, so installation, native loading, and the displayed test results have
not yet been verified. GitHub Actions has not yet been confirmed to run.
