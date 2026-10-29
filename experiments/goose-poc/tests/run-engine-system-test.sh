#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to a JDK}"
: "${OUT_DIR:?Set OUT_DIR to the output of run-engine-bridge-test.sh}"
root=$(cd "$(dirname "$0")/../../.." && pwd)
testroot="$root/experiments/goose-poc/tests/integration"
src="$root/catroid/src/main/java/org/catrobat/catroid/fast2d"
"$JAVA_HOME/bin/javac" -cp "$OUT_DIR/classes" -d "$OUT_DIR/classes" \
  "$testroot"/com/artemis/*.java "$testroot"/com/artemis/systems/*.java "$testroot"/com/artemis/utils/*.java \
  "$testroot"/com/badlogic/gdx/graphics/*.java "$testroot"/com/badlogic/gdx/graphics/g2d/*.java \
  "$src/components/MovementComponent.java" "$src/components/TransformComponent.java" "$src/components/TextureComponent.java" \
  "$src/systems/Fast2DMovementSystem.java" "$src/systems/Fast2DRenderSystem.java" "$testroot/MovementSystemTest.java" "$testroot/RenderSystemTest.java"
"$JAVA_HOME/bin/java" -Djava.library.path="$OUT_DIR" -cp "$OUT_DIR/classes" MovementSystemTest

"$JAVA_HOME/bin/java" -Djava.library.path="$OUT_DIR" -cp "$OUT_DIR/classes" RenderSystemTest

# Compile the same production classes with Goose disabled in a separate JVM.
mkdir -p "$OUT_DIR/fallback-src/org/catrobat/catroid" "$OUT_DIR/fallback-classes"
printf '%s\n' 'package org.catrobat.catroid; public final class BuildConfig { public static final boolean FEATURE_GOOSE_FAST2D = false; }' > "$OUT_DIR/fallback-src/org/catrobat/catroid/BuildConfig.java"
"$JAVA_HOME/bin/javac" -d "$OUT_DIR/fallback-classes" \
  "$OUT_DIR/fallback-src/org/catrobat/catroid/BuildConfig.java" "$src/GooseMovement.java" \
  "$testroot"/com/artemis/*.java "$testroot"/com/artemis/systems/*.java "$testroot"/com/artemis/utils/*.java \
  "$testroot"/com/badlogic/gdx/graphics/*.java "$testroot"/com/badlogic/gdx/graphics/g2d/*.java \
  "$src/components/MovementComponent.java" "$src/components/TransformComponent.java" "$src/components/TextureComponent.java" \
  "$src/systems/Fast2DMovementSystem.java" "$src/systems/Fast2DRenderSystem.java" "$testroot/FallbackSystemTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT_DIR/fallback-classes" FallbackSystemTest
