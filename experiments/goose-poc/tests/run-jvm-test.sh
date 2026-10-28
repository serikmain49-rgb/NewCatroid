#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME to a JDK}"
: "${OUT_DIR:?Set OUT_DIR to the output from the host build.sh}"
root=$(cd "$(dirname "$0")/.." && pwd)
"${CC:-gcc}" -std=c11 -O2 -ffp-contract=off -fPIC -shared \
  -I "$OUT_DIR" -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
  "$OUT_DIR/math.c" "$root/jni.c" -o "$OUT_DIR/libgoose_poc.so" -lm -ldl -pthread
mkdir -p "$OUT_DIR/classes"
"$JAVA_HOME/bin/javac" -d "$OUT_DIR/classes" \
  "$root/android/src/org/catrobat/catroid/goosepoc/GooseProbe.java" "$root/tests/MovementBatchTest.java"
"$JAVA_HOME/bin/java" -Djava.library.path="$OUT_DIR" -cp "$OUT_DIR/classes" org.catrobat.catroid.goosepoc.MovementBatchTest
