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
check is included in the Android test APK. The experimental engine passes one packed array per movement-system update
to a Goose batch function. This is an integration experiment, not a performance
measurement.
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
standalone APK. GitHub Actions run 40830529372 completed successfully.

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

## Experimental Fast2D integration

The app now includes a generated Goose C runtime and JNI wrapper. The movement
system can use it with `./gradlew :catroid:assembleDanvexDebug
-PgooseFast2D=true`; without that property, Java remains active. If the native
library cannot load or a call occurs on a different thread, the Java path is
used. The generated file is reproducible with `generate-engine-c.sh` using the
pinned compiler; the source is `math.goose`. The integration now makes one JNI call per non-empty system update, and is
not claimed to be faster. It has not been tested in
the actual app or on a device. In particular, Android lifecycle and thread
ownership, float edge cases, project loading, interactions with Box2D and app
build compatibility need validation before it can be enabled by default.

The application JNI bridge passed a Linux JVM smoke test and both native ABIs
cross-compiled. The local Gradle attempt failed before configuration because the distribution
could not be downloaded. GitHub Actions run 40829976442 subsequently built
the full `danvexDebug` APK successfully with `-PgooseFast2D=true` and uploaded
its log and APK as `newcatroid-goose-debug`. The APK contains the native Goose
library for ARM64 and ARMv7; it has not been launched or verified on a device.
It uses the original application ID and must be treated as an experimental
debug build; installation may replace the existing app if Android accepts its
signature. APK creation does not certify project or plugin compatibility.


The Goose batch kernel was checked on Linux against 257 entities for 120
frames in addition to the existing scalar tests. The changed Android build and
actual Fast2D integration require CI and device validation; the earlier APK
predates this batch change. Existing API fallback, frame ordering, and Box2D
behavior must be verified before enabling it by default.


The experimental flag also routes `ThreeDManager.getDistance` through the Goose
3D distance function when the bridge is usable. A null object retains the
existing -1 result, and otherwise the original libGDX method is the fallback.
The C host compares 100,000 finite distance cases with float arithmetic and a
double square root. This does not port the 3D renderer, physics or camera, and
the actual 3D path still needs a full app build and Android device verification.
The scalar JNI wrapper allocates a small Java output array per call, so no
speed or allocation improvement is claimed.

The application workflow also attempts an x86-64 API 30 emulator installation and
launcher smoke test after a successful APK build, recording logcat. This checks
only that the application process remains alive after startup; it does not run a
project, measure performance, or validate ARM, rendering, physics or plugins.

The experimental renderer also passes packed 2D bounding rectangles to a
Goose visibility function before drawing. SpriteBatch drawing, texture access,
shader state and z-order sorting remain in libGDX/Java. The Goose expression
retains the original comparisons including edge-touch behavior; the C host
comparison includes finite, NaN and infinite values. A JVM test uses the real
Fast2D renderer with graphics test doubles to check visibility and drawing
order. These changes require CI and Android validation; no performance gain has
been established and the fallback path stays available.

On 2026-10-29, GitHub Actions run 40840973551 built the application with the
movement, 2D visibility and 3D distance prototypes enabled and launched its
main activity on an x86-64 API 30 Android emulator; the process remained alive
through the smoke check. This does not establish ARM behavior or project
compatibility. The workflow now also selects existing formula and project unit
tests. Their results have not been observed yet and are not a substitute for
running representative real projects and plugins.

The optional Fast2D physics system now asks Goose for its fixed-step schedule:
up to three 1/60-second steps and the retained remainder. Box2D still performs
all simulation and collision work, in the existing order. A host C comparison
covers 100,000 accumulated values including NaN/infinity, and a JVM test checks
the real Fast2D physics system with a Box2D test double over 5,000 frames.
The new scheduler has not yet been verified on Android or real Box2D projects;
it is not a port of Box2D and is not a measured speed improvement.
