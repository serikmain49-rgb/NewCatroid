package com.badlogic.gdx.physics.box2d;
public class World {
    public int calls;
    public void step(float step, int velocityIterations, int positionIterations) {
        if (step != 1f / 60f || velocityIterations != 4 || positionIterations != 1)
            throw new AssertionError("physics step parameters changed");
        calls++;
    }
    public void destroyBody(Body body) { }
}
