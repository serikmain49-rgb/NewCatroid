package org.catrobat.catroid.goosepoc;

public final class GooseProbe {
    static { System.loadLibrary("goose_poc"); }
    private GooseProbe() { }
    public static native int add(int a, int b);
}
