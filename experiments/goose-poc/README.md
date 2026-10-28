# Goose experiment (not integrated into NewCatroid)

The experiment exports scalar functions from Goose, compiles their generated C
with Android NDK r27c for ARM64 and ARMv7, and packages an independent JNI test
APK. It does not replace an engine component or establish performance or project
compatibility. Goose is pinned to cce94b919d27458856ae2685974049eac78debcf.

## Fast2D movement

`math.goose` implements the gravity-first arithmetic from
`Fast2DMovementSystem.process`. The scalar test passed 100,000 finite cases on
Linux. A JVM test checks 512 objects over 120 frames, bit-for-bit against the
Java arithmetic, plus null, invalid-length and empty-array cases. The same batch
check is included in the Android test APK. JNI receives an array once per frame;
C iterates over the array and invokes the scalar Goose functions. This is not a
Goose batch kernel, an engine integration, or a performance measurement.
Floating-point contraction is disabled for the experiment.

Two screenshots supplied by the user on 2026-10-29 show the first Android test
passed the scalar addition and wrong-thread checks, and the second passed those
checks plus one comparison of the Fast2D arithmetic with Java. They do not
identify device model, ABI or Android version; ARMv7 and 32-bit runtime behavior
remain unverified. The newer batch test has not yet run on a device.

The generated header of the pinned compiler omits `gs_i32` and `gs_f32` scalar
typedefs. `build.sh` substitutes `int32_t` and `float` for these two symbols in
the header. This is a limited workaround, not a general Goose ABI repair.
Goose calls must remain on the initializing thread; the JNI wrapper rejects calls
from others. The runtime's large default virtual stack reservation needs
investigation on 32-bit Android.

## Build and verify

Build Goose, then run `build.sh` with `GOOSE`, `CC`, and `OUT_DIR` set. Run
`$OUT_DIR/smoke` and `tests/run-jvm-test.sh` with `JAVA_HOME` and `OUT_DIR` set
on Linux. The latter builds and exercises the JNI bridge with a host JVM.
The workflow cross-compiles both Android ABIs, tests the host and builds a
standalone APK; GitHub Actions execution itself has not been confirmed.

For the test APK, set `ANDROID_HOME` to an SDK containing platform 34 and
build-tools 34.0.0, and `GOOSE_RESULTS` to a directory containing the output
folders `aarch64-linux-android26` and `armv7a-linux-androideabi26`. Then run
`android/build-apk.sh` with JDK 17 on PATH. It signs with a local debug key,
not suitable for production. The APK is a separate app requiring Android 8+
and has no permissions. A new debug key prevents updating an earlier APK;
the prior test must then be uninstalled.

No baseline profile, FPS result, overall compatibility, physics behavior, or
performance gain has been established. Render and lifecycle ordering, plugins,
Assistant, project serialization, and production thread ownership remain to be
verified before any integration.
