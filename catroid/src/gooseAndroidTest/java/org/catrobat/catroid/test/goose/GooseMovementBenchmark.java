package org.catrobat.catroid.test.goose;

import android.util.Log;
import org.catrobat.catroid.fast2d.GooseMovement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** JNI microbenchmark only: no rendering, physics, device thermals or full scenes. */
final class GooseMovementBenchmark {
    private static final int STRIDE = 7;
    private static final int WARMUP = 3000;
    private static final int ITERATIONS = 10000;
    private static final float DT = 1f / 60f;

    private static void updateJava(float[] values, int count) {
        for (int i = 0; i < count * STRIDE; i += STRIDE) {
            values[i + 4] += values[i + 5] * DT;
            values[i] += values[i + 3] * DT;
            values[i + 1] += values[i + 4] * DT;
            values[i + 2] += values[i + 6] * DT;
        }
    }

    static void run() {
        for (int count : new int[] {1, 10, 100, 1000}) {
            float[] javaValues = new float[count * STRIDE];
            for (int i = 0; i < count; i++) {
                int at = i * STRIDE;
                javaValues[at] = i; javaValues[at + 1] = -i;
                javaValues[at + 3] = 0.5f; javaValues[at + 4] = 0.25f;
                javaValues[at + 5] = -0.1f; javaValues[at + 6] = 0.125f;
            }
            float[] gooseValues = javaValues.clone();
            for (int i = 0; i < WARMUP; i++) {
                updateJava(javaValues, count);
                assertTrue(GooseMovement.updateBatch(gooseValues, count, DT));
            }
            long started = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) updateJava(javaValues, count);
            long javaNs = System.nanoTime() - started;
            started = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                if (!GooseMovement.updateBatch(gooseValues, count, DT))
                    throw new AssertionError("Goose backend unavailable during benchmark");
            }
            long gooseNs = System.nanoTime() - started;
            for (int i = 0; i < javaValues.length; i++)
                assertEquals("result " + i, Float.floatToIntBits(javaValues[i]), Float.floatToIntBits(gooseValues[i]));
            Log.i("GOOSE_MICRO", "entities=" + count + " iterations=" + ITERATIONS
                    + " java_ns_per_batch=" + javaNs / ITERATIONS
                    + " goose_jni_ns_per_batch=" + gooseNs / ITERATIONS);
        }
    }
}
