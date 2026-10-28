package org.catrobat.catroid.fast2d;

import org.catrobat.catroid.BuildConfig;

/** Optional Fast2D arithmetic backend. The caller owns and reuses the result buffer. */
public final class GooseMovement {
    private static final boolean AVAILABLE = load();

    private GooseMovement() { }

    private static boolean load() {
        if (!BuildConfig.FEATURE_GOOSE_FAST2D) return false;
        try {
            System.loadLibrary("goose_movement");
            return true;
        } catch (LinkageError | SecurityException error) {
            return false;
        }
    }

    public static boolean update(float x, float y, float rotation, float vx, float vy,
                                 float gravity, float angularVelocity, float delta, float[] result) {
        if (!AVAILABLE) return false;
        try {
            return step(x, y, rotation, vx, vy, gravity, angularVelocity, delta, result);
        } catch (LinkageError error) {
            return false;
        }
    }

    private static native boolean step(float x, float y, float rotation, float vx, float vy,
                                       float gravity, float angularVelocity, float delta, float[] result);
}
