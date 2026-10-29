#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to a JDK}"
: "${OUT_DIR:?Set OUT_DIR to the output of run-engine-bridge-test.sh}"
root=$(cd "$(dirname "$0")/../../.." && pwd)
testroot="$root/experiments/goose-poc/tests/integration"
src="$root/catroid/src/main/java/org/catrobat/catroid/fast2d"
"$JAVA_HOME/bin/javac" -cp "$OUT_DIR/classes" -d "$OUT_DIR/classes" \
  "$testroot"/com/artemis/*.java "$testroot"/com/artemis/systems/*.java \
  "$src/components/MovementComponent.java" "$src/components/TransformComponent.java" \
  "$src/systems/Fast2DMovementSystem.java" "$testroot/MovementSystemTest.java"
"$JAVA_HOME/bin/java" -Djava.library.path="$OUT_DIR" -cp "$OUT_DIR/classes" MovementSystemTest
