package com.artemis.systems;
import com.artemis.Aspect;
import com.artemis.World;
public abstract class IteratingSystem {
    protected World world;
    protected IteratingSystem(Aspect aspect) { }
    protected void begin() { }
    protected void end() { }
    protected abstract void process(int entityId);
}
