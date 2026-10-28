package org.catrobat.catroid.goosepoc;

public final class GooseProbe {
    static { System.loadLibrary("goose_poc"); }
    private GooseProbe() { }
    public static native int add(int a, int b);
    public static native float[] movement(float x, float y, float rotation, float vx, float vy, float gravity, float angular, float dt);
}
