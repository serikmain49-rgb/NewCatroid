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

    public static boolean isAvailable() { return AVAILABLE; }

    public static boolean updateBatch(float[] data, int count, float delta) {
        if (!AVAILABLE) return false;
        try {
            return batch(data, count, delta);
        } catch (LinkageError error) {
            return false;
        }
    }

    public static boolean sortDepth(int[] ids, float[] depths, int count) {
        if (!AVAILABLE) return false;
        try {
            return sortDepthNative(ids, depths, count);
        } catch (LinkageError error) {
            return false;
        }
    }

    public static boolean physicsPlan(float accumulator, float timeStep, float[] out) {
        if (!AVAILABLE) return false;
        try {
            return physicsPlanNative(accumulator, timeStep, out);
        } catch (LinkageError error) {
            return false;
        }
    }

    public static boolean visibility(float[] data, int count, float camX, float camY, float halfW, float halfH) {
        if (!AVAILABLE) return false;
        try {
            return visibilityBatch(data, count, camX, camY, halfW, halfH);
        } catch (LinkageError error) {
            return false;
        }
    }

    public static boolean distance(float ax, float ay, float az, float bx, float by, float bz, float[] out) {
        if (!AVAILABLE) return false;
        try {
            return distance3d(ax, ay, az, bx, by, bz, out);
        } catch (LinkageError error) {
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

    /** Returns -1 for Java fallback, 0 for outside, or 1 for inside. */
    public static int hitTest(float dx, float dy, float cosine, float sine,
                              boolean rotated, float halfWidth, float halfHeight) {
        if (!AVAILABLE) return -1;
        try {
            return hitTestNative(dx, dy, cosine, sine, rotated, halfWidth, halfHeight);
        } catch (LinkageError error) {
            return -1;
        }
    }

    private static native int hitTestNative(float dx, float dy, float cosine, float sine,
                                            boolean rotated, float halfWidth, float halfHeight);

    /** Called by StageListener on its render thread before executing game scripts. */
    public static boolean prepareGameThread() {
        if (!AVAILABLE) return false;
        try {
            return initializeForGameThread();
        } catch (LinkageError error) {
            return false;
        }
    }

    private static native boolean initializeForGameThread();

    /** Evaluate a bounded numeric postfix program; false leaves the Java path authoritative. */
    public static boolean formulaNumeric(int[] ops, double[] values, int count, double[] out) {
        if (!AVAILABLE) return false;
        try {
            return formulaNumericNative(ops, values, count, out);
        } catch (LinkageError error) {
            return false;
        }
    }

    private static native boolean formulaNumericNative(int[] ops, double[] values, int count, double[] out);
    private static native boolean sortDepthNative(int[] ids, float[] depths, int count);
    private static native boolean physicsPlanNative(float accumulator, float timeStep, float[] out);
    private static native boolean visibilityBatch(float[] data, int count, float camX, float camY, float halfW, float halfH);
    private static native boolean distance3d(float ax, float ay, float az, float bx, float by, float bz, float[] out);
    private static native boolean batch(float[] data, int count, float delta);

    private static native boolean step(float x, float y, float rotation, float vx, float vy,
                                       float gravity, float angularVelocity, float delta, float[] result);
}
