#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-results
trap 'adb logcat -d -v time > emulator-results/logcat.txt || true' EXIT
adb logcat -c
apk=(goose-build-results/*.apk)
if [[ ${#apk[@]} != 1 || ! -f ${apk[0]} ]]; then
    echo 'Expected exactly one APK in goose-build-results' >&2
    exit 1
fi
adb install -r "${apk[0]}"
adb shell monkey -p org.DanVexTeam.NewCatroid -c android.intent.category.LAUNCHER 1
sleep 20
adb shell pidof org.DanVexTeam.NewCatroid
chmod +x ./gradlew
./gradlew :catroid:connectedDanvexDebugAndroidTest -PgooseFast2D=true -PgooseDeviceTestsOnly=true --no-daemon --stacktrace \
    -Pandroid.testInstrumentationRunnerArguments.class=org.catrobat.catroid.test.goose.GooseNativeDeviceTest \
    2>&1 | tee emulator-results/instrumentation.log
