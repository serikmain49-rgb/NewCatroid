# Initial source map and open questions

Baseline NewCatroid commit: 39db5865d04ee74b08503f0b7ef35158436e6b98.
This is a partial audit, not an inventory of every behavior or a compatibility certification.

| Area | Observed source and dependencies |
| --- | --- |
| Android build | `settings.gradle` includes catroid, lunoscript-processor, lunoscript-annotations, vncclient. `catroid/build.gradle` sets compile SDK 34, minimum 26, target 33, and existing CMake native build. Root build uses AGP 8.6.0 and Kotlin 1.9.22. |
| Frame and lifecycle | `stage/StageListener.java` owns render, stage `act` and pause/resume paths; frame ordering needs behavioral tests before changes. |
| Standard blocks | `content/bricks`, `content/actions`, `content/ActionFactory.java`, and formula interpreter classes implement behavior using scene2d actions and formulas; Android and external-service blocks cannot simply move to native code. |
| Fast 2D | `fast2d/FastTwoDManager.java` configures Artemis world, Box2D world and systems; `fast2d/systems` and `components` implement movement, physics and rendering. |
| 3D | `raptor/ThreeDManager.java` uses libGDX ModelBatch, gdx-gltf SceneManager and Bullet dynamics; `raptor/SceneManager.java`, GameObject and components add scene-level features. Existing animation, postprocessing and resource lifetimes require audit. |
| Assets and audio | `io/SoundManager.java`, `raptor/SoundWrapper.java`, `raptor/MusicWrapper.java`; image and model paths and lifecycle need tracing. |
| Persistence | `ProjectManager.java`, `io/StorageOperations.java`, `io/BackwardCompatibleCatrobatLanguageXStream.java` and XStream converters are compatibility-sensitive. `code.xml` appears in project tooling. The role of project.json and Assistant bridge must be verified separately. |
| Plugins | `plugins/PluginManager.java`, `PluginExecutor.java`, `PluginEventBus.java` and `CatroidApplication.PluginCompositeClassLoader` are existing integration points. |

The app depends on libGDX 1.13.1, Box2D and Bullet native artifacts for both
ARM ABIs, gdx-gltf 2.2.1 and XStream 1.4.11.1. Replacing these blindly risks
changing physics, rendering, script ordering, serialization and plugin APIs.

Goose's documented C export requires `goose_init` and calls on the initializing
thread. Generated C is not proof of Android NDK, Bionic, ARMv7, JNI, OpenGL ES,
or Vulkan support. Its default data-stack virtual address reservation is a
specific concern for 32-bit processes. Graphics APIs could potentially be
called through C bindings, but no such integration has been tested here.

Before engine integration: run this experiment on real Android, profile the
unmodified app on reproducible scenes, trace project formats and Assistant,
add regression fixtures for block ordering, persistence, physics and plugins,
and select an actually measured hotspot. Record FPS distributions, frame CPU
and GPU times, memory, load times, draw calls, thermal and power methodology;
no baseline measurements have been collected yet.

Experiment update: Goose cce94b919d27458856ae2685974049eac78debcf built on
Linux; its generated standalone C passed the host smoke test after correcting
an undefined `gs_i32` in the generated header. NDK r27c/API 26 cross-compiled
both the executable and JNI library for ARM64 and ARMv7. This tests only a
scalar addition and native linking; it is not an Android runtime test. The
existing project formats, 3D/2D rendering, block behavior, Assistant, and
performance have not been tested by this experiment.

Device update (2026-10-29): the user supplied a screenshot of the original
test app showing `PASS: Goose JNI returned 42 and 1` and
`PASS: wrong-thread call rejected`. Device model, ABI, Android version and
32-bit behavior have not been independently identified.

The isolated Goose movement arithmetic now matches a C reference in 100,000
deterministic finite host cases; it is not integrated into Fast2D. No baseline
profile or speedup is established.

Second user screenshot (2026-10-29): all three checks passed, including the
single Android comparison of Goose movement arithmetic against Java. No device
model or ABI was independently established. A newer batch prototype and test
have been added: JNI transfers one seven-float record per object, the C bridge
iterates and calls the scalar Goose functions. On Linux, 512 objects over 120
frames matched Java bit-for-bit, with malformed input and empty-array tests;
the newer Android test has been compiled but not run on a device. This remains
unintegrated and unbenchmarked.

## Frame ordering and compatibility observations

Inspection of `StageListener.render` found a paused guard around scene update,
2D Box2D stepping and scene2d `act`; later in the same rendering path it updates
3D, renders 3D, calls `FastTwoDManager.updateAndRender`, then draws the stage.
Fast2D is updated only when not paused and its main render target is enabled.
The deltas are not all identical: scene update uses a capped delta, while Fast2D
receives `Gdx.graphics.getDeltaTime()`. Do not unify timing or reorder those
operations without compatibility tests. There are additional stage paths,
including an earlier update path around `StageListener.java:530`, which require
separate tracing.

`FastTwoDManager.init` registers the movement, physics and render systems in
that order. `Fast2DPhysicsSystem` steps Box2D in fixed 1/60-second increments
with a maximum of three steps and overwrites transforms from awake bodies.
Movement is not a replacement for Box2D. `Fast2DRenderSystem` already uses a
SpriteBatch, a camera rectangle visibility check, and a dirty flag for sorting;
any additional culling or batching needs measurement and regression tests.

`ide/ProjectManager.kt` reads and writes a `project.json` for its IDE project
configuration; this is not sufficient evidence that it is the Assistant bridge's
exported `project.json`. The bridge format and Assistant plugin must be audited
from their own source. Existing XStream compatibility classes remain in use and
must not be replaced on the assumption that all projects share this IDE format.

## Assistant 0.7.0 compatibility boundary

The user's `NewCatroid-Assistant-0.7.0.nplug` archive was inspected (not
installed or modified). Its `Main` and `Bridge` use reflection to access the
Java project model, `ProjectManager`, `SceneController` and
`io.XstreamSerializer`, and export `project.json` alongside `code.xml` and the
block catalogue. The Goose branch still keeps these existing classes; this is
not a complete compatibility test.

The plugin's optional audio integration refers to `audio.RuntimeAudioSession`
and `audio.ImportedAudioStore`. These classes are absent from the GitHub base
revision used by this branch. The user also has a separate
`NewCatroid-Runtime-Audio-Source-0.7.0.zip` with a candidate implementation.
Until that separate change is reconciled and tested, the Goose branch cannot
be claimed to support the plugin's runtime-audio features. Normal plugin
behavior has not been verified on a device either.

The prior `NewCatroid-Runtime-Audio-Source-0.7.0.zip` source snapshot was
reconciled against the GitHub base using three-way merges, without changing the
Goose feature flag. This adds the optional audio host APIs and blocks required by
Assistant 0.7.0, alongside their earlier automated test sources. The merge has
not yet been built or tested on a device in this branch; audio codec behavior,
plugin compatibility, permissions and existing projects remain unverified.

Compatibility test finding: run 40842553281 failed two legacy PowerMock checks
because Java 17 did not open `java.lang`, and `FormulaElementTest.testCorrectDecimals`
expected 1.2 exactly while the interpreter returned 1.2000000000000002. The
arithmetic branch in `FormulaElement` is unchanged from the GitHub base; this
suggests a pre-existing exact-floating-point expectation, but a clean baseline
run has been configured and is still needed to confirm it. Java 17 unit-test
module opens were added without changing formula results. These failures must
not be reported as passing or silently excluded.

## Status update: numeric formula prototype

The original decimal formula test also failed against the unmodified baseline
in CI run 40844845793. This confirms that particular failing expectation is
not evidence of a Goose regression; the test itself has not been changed.

An experimental flag, `-PgooseFormula=true`, enables a Goose numeric postfix
interpreter for pure literal trees with addition, subtraction, multiplication,
division, unary minus, comparisons, Boolean operators and brackets. Other nodes are executed by the unchanged
Java interpreter. Project XML and plugin model classes are retained. Host tests
compare 20,000 randomly generated trees and 40,000 binary operations with a
Java reference, with NaNs, signed zero and overflow, and reject invalid native
programs. The original production `FormulaElement` integration and the new
Android test still need a successful Android CI run. This prototype is rebuilt
on every evaluation to avoid caching stale mutable formula trees, and has no
measured speed benefit. It is disabled by default.

Formula evaluation may also happen on an editor or Android UI thread. Its JNI
entry therefore cannot initialize or take ownership of the Goose runtime. With
formula lowering enabled, `StageListener.render` explicitly attempts to
initialize on the game render thread before executing scripts; calls from other
threads or after the original owner exits fall back to Java. The pinned Goose
runtime still does not support transferring its owner to a replacement thread.

## Preliminary movement microbenchmark

On this Linux x86-64 host with OpenJDK 17, running the Android benchmark class
with minimal `Log` and JUnit stubs, one run used 3,000 warmup and 10,000 timed
iterations per case. Average nanoseconds per packed batch were:

| Records | Java | Goose through JNI |
| ---: | ---: | ---: |
| 1 | 35 | 73 |
| 10 | 34 | 103 |
| 100 | 223 | 531 |
| 1000 | 1300 | 2894 |

This is a single host microbenchmark with simplified arrays, no ECS packing,
rendering, or physics. Results are sensitive to JIT and scheduling and cannot
predict Android or FPS. In this run Goose was slower; a performance claim or a
default-on setting is not justified. The same instrumentation workload is
available for Android, where its separate results must be measured.

Depth ordering is a separate case: for ordinary depth values the Goose path
now uses stable merge sort, while NaN retains the exact original insertion
comparison behavior. Randomized JNI tests compare against the original sort,
including repeated depths, NaN, infinity and arrays up to 2,000 elements.
A single host run of the reverse-order microbenchmark measured these average
nanoseconds per sort (including array resets and JNI copying):

| Records | Java insertion | Goose/JNI merge |
| ---: | ---: | ---: |
| 100 | 11,820 | 1,651 |
| 1,000 | 292,228 | 17,838 |
| 5,000 | 8,089,760 | 121,265 |

The Java microbenchmark sorts primitive arrays, not the real ECS component
lookups; reverse order is an insertion-sort worst case. These are host-only
measurements, not Android, frame-time or whole-game performance results.

## Goose runtime embedding and Android checks

The pinned Goose runtime normally installs process-wide SIGSEGV/SIGBUS handlers,
sets up an alternate signal stack and changes stdout buffering during init.
That is intrusive when embedded in an Android/JVM process. For this specific
numeric-only module, the compiler reports zero main and worker Goose data
stacks. A deterministic generation-time adapter and `NC_GOOSE_PURE_EMBED`
compile flag preserve the host's signal handlers, alternate stack and stdout
buffering. The adapter refuses generation if that zero-stack property or the
runtime initialization pattern changes; using it for an allocating Goose module
requires a new runtime design. A host native test confirms signal handlers and
the alternate stack remain unchanged. This does not by itself prove all ART,
crash-handler and Android ABI interactions.

GitHub Actions run 40862940083 successfully built the pre-formula APK, launched
it on an x86_64 Android 11 emulator, and ran one production JNI instrumentation
test successfully (movement, visibility, distance, physics plan and depth).
It also passed the runtime-audio host checks with Android/player doubles. This
is not a real-device, ARM, full Fast2D scene, codec, plugin, or performance
verification. The newer formula, sort, real Box2D and embedded-runtime changes
need their own completed CI results.
