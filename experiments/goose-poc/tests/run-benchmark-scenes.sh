#!/usr/bin/env bash
set -euo pipefail
: "${BENCH_BACKEND:?Set BENCH_BACKEND to true or false}"
bench_results_dir=${BENCH_RESULTS_DIR:-benchmark-results}
mkdir -p "$bench_results_dir"
trap 'adb logcat -d -v time -s ActivityTaskManager:I LifecycleMonitor:D StageActivity:I RENDER_CRASH:E AndroidRuntime:E TestRunner:I GOOSE_SCENARIO:I GOOSE_BENCHMARK:I "*:S" > "$bench_results_dir/logcat.txt" || true' EXIT
chmod +x ./gradlew
for count in 10 100 1000; do
  adb logcat -c
  ./gradlew :catroid:connectedDanvexDebugAndroidTest \
    -PgooseFast2D="$BENCH_BACKEND" -PgooseBenchmark=true -PgooseDeviceTestsOnly=true \
    -Pandroid.testInstrumentationRunnerArguments.class=org.catrobat.catroid.test.goose.GooseBenchmarkSceneTest \
    -Pandroid.testInstrumentationRunnerArguments.scenarioCount="$count" \
    --no-daemon --stacktrace 2>&1 | tee "$bench_results_dir/scene-$count.log"
  adb logcat -d -v time -s GOOSE_SCENARIO:I GOOSE_BENCHMARK:I "*:S" > "$bench_results_dir/metrics-$count.log"
  grep -q "phase=start.*objects=$count" "$bench_results_dir/metrics-$count.log"
  grep -q "phase=end.*objects=$count" "$bench_results_dir/metrics-$count.log"
  # A second full window must exist after the setup marker.
  awk '/phase=start/ {active=1; next} /phase=end/ {active=0} active && /GOOSE_BENCHMARK/ {n++} END {exit n<2}' "$bench_results_dir/metrics-$count.log"
done
