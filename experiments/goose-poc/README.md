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
