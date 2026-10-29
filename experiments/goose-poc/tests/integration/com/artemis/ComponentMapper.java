package com.artemis;
public final class ComponentMapper<T> {
    private final T value;
    public ComponentMapper(T value) { this.value = value; }
    public T get(int id) { return value; }
}
