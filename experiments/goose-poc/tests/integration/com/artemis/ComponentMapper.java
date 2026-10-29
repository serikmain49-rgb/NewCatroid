package com.artemis;
public final class ComponentMapper<T> {
    private final T value;
    private final T[] values;
    public ComponentMapper(T value) { this.value = value; this.values = null; }
    public ComponentMapper(T[] values) { this.value = null; this.values = values; }
    public T get(int id) { return values == null ? value : values[id]; }
}
