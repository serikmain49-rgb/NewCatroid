package com.artemis.utils;
public final class IntBag {
    private int[] values = new int[0];
    public void set(int[] values) { this.values = values; }
    public int[] getData() { return values; }
    public int size() { return values.length; }
}
