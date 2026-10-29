#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to a JDK}"
: "${OUT_DIR:?Set OUT_DIR to a writable directory}"
root=$(cd "$(dirname "$0")/../../.." && pwd)
native="$root/catroid/src/main/cpp/goose"
mkdir -p "$OUT_DIR/classes"
"${CC:-gcc}" -std=c11 -O2 -ffp-contract=off -DNC_GOOSE_PURE_EMBED=1 -shared -fPIC -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" "$native/movement_generated.c" "$native/movement_jni.c" -o "$OUT_DIR/libgoose_movement.so" -lm -ldl -pthread
"${CC:-gcc}" -std=c11 -O2 -ffp-contract=off -DNC_GOOSE_PURE_EMBED=1 \
  -I "$native" "$native/movement_generated.c" "$root/experiments/goose-poc/tests/embedded_signal_test.c" \
  -o "$OUT_DIR/embedded-signal-test" -lm -ldl -pthread
"$OUT_DIR/embedded-signal-test"
# Fail closed if the pure embed is ever compiled with Goose workers enabled.
if "${CC:-gcc}" -std=c11 -DNC_GOOSE_PURE_EMBED=1 -DGS_NEED_THREADS=1 \
    -c "$native/movement_generated.c" -o "$OUT_DIR/unsupported-workers.o" 2>"$OUT_DIR/unsupported-workers.log"; then
  echo 'Expected Goose worker configuration to be rejected' >&2
  exit 1
fi
grep -q 'pure Goose adapter does not support worker threads' "$OUT_DIR/unsupported-workers.log"
"$JAVA_HOME/bin/javac" -d "$OUT_DIR/classes" "$root/experiments/goose-poc/tests/integration/org/catrobat/catroid/BuildConfig.java" "$root/catroid/src/main/java/org/catrobat/catroid/fast2d/GooseMovement.java" "$root/experiments/goose-poc/tests/integration/TestIntegration.java" "$root/experiments/goose-poc/tests/integration/OwnerThreadLifecycleTest.java"
"$JAVA_HOME/bin/java" -Djava.library.path="$OUT_DIR" -cp "$OUT_DIR/classes" TestIntegration

"$JAVA_HOME/bin/java" -Djava.library.path="$OUT_DIR" -cp "$OUT_DIR/classes" OwnerThreadLifecycleTest

# Compile the production numeric lowering with a minimal mutable model fixture.
formula="$root/experiments/goose-poc/tests/formula/org/catrobat/catroid/formulaeditor"
"$JAVA_HOME/bin/javac" -cp "$OUT_DIR/classes" -d "$OUT_DIR/classes" \
  "$formula/FormulaElement.java" \
  "$root/catroid/src/main/java/org/catrobat/catroid/formulaeditor/GooseNumericProgram.java" \
  "$formula/GooseNumericProgramTest.java"
"$JAVA_HOME/bin/java" -Djava.library.path="$OUT_DIR" -cp "$OUT_DIR/classes" org.catrobat.catroid.formulaeditor.GooseNumericProgramTest
