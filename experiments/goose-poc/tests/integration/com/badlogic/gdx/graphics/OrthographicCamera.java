package com.badlogic.gdx.graphics;
public class OrthographicCamera {
    public static final class Position { public float x, y; }
    public final Position position = new Position();
    public float viewportWidth, viewportHeight, zoom = 1f;
    public Object combined = new Object();
}
