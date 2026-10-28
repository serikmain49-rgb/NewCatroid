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
