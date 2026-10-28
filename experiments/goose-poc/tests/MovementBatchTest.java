package org.catrobat.catroid.goosepoc;

import java.util.Random;

/** Shared host-JVM and Android correctness test; no benchmark claims. */
public final class MovementBatchTest {
    public static void run() {
        Random random = new Random(123456);
        float[] expected = new float[512 * 7];
        for (int i = 0; i < expected.length; i++) expected[i] = (random.nextFloat() - 0.5f) * 100.0f;
        float[] actual = expected.clone();
        for (int frame = 0; frame < 120; frame++) {
            float dt = (frame % 3 == 0) ? 0.016f : 0.033f;
            for (int i = 0; i < expected.length; i += 7) {
                expected[i + 4] += expected[i + 5] * dt;
                expected[i] += expected[i + 3] * dt;
                expected[i + 1] += expected[i + 4] * dt;
                expected[i + 2] += expected[i + 6] * dt;
            }
            GooseProbe.movementBatch(actual, dt);
            for (int i = 0; i < expected.length; i++) {
                if (Float.floatToIntBits(expected[i]) != Float.floatToIntBits(actual[i])) {
                    throw new AssertionError("frame=" + frame + " index=" + i + " expected=" + expected[i] + " actual=" + actual[i]);
                }
            }
        }
        GooseProbe.movementBatch(new float[0], 0f);
        try {
            GooseProbe.movementBatch(new float[6], 0f);
            throw new AssertionError("invalid length accepted");
        } catch (IllegalArgumentException expectedError) { }
        try {
            GooseProbe.movementBatch(null, 0f);
            throw new AssertionError("null accepted");
        } catch (IllegalArgumentException expectedError) { }
    }
    public static void main(String[] args) {
        run();
        System.out.println("PASS: 512 entities, 120 frames, Java versus Goose JNI");
    }
}
