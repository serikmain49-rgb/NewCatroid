package org.catrobat.catroid.fast2d.systems;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;

import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.fast2d.components.PhysicsComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;

public class Fast2DPhysicsSystem extends IteratingSystem {

    public static final float PPM = 100f;

    protected ComponentMapper<TransformComponent> mTransform;
    protected ComponentMapper<PhysicsComponent> mPhysics;

    private final World physicsWorld;
    private float accumulator = 0f;
    private final float[] goosePlan = new float[2];
    private static final float TIME_STEP = 1f / 60f;

    public Fast2DPhysicsSystem(World world) {
        super(Aspect.all(TransformComponent.class, PhysicsComponent.class));
        this.physicsWorld = world;
    }

    @Override
    protected void removed(int entityId) {
        PhysicsComponent phys = mPhysics.get(entityId);
        if (phys != null && phys.body != null) {
            physicsWorld.destroyBody(phys.body);
            phys.body = null;
        }
    }

    @Override
    protected void begin() {
        float deltaTime = world.getDelta();
        accumulator += deltaTime;

        int maxSteps = 3;
        if (GooseMovement.physicsPlan(accumulator, TIME_STEP, goosePlan)) {
            int steps = (int) goosePlan[0];
            for (int i = 0; i < steps; i++) {
                physicsWorld.step(TIME_STEP, 4, 1);
                accumulator -= TIME_STEP;
            }
            accumulator = goosePlan[1];
            return;
        }

        while (accumulator >= TIME_STEP && maxSteps > 0) {
            physicsWorld.step(TIME_STEP, 4, 1);
            accumulator -= TIME_STEP;
            maxSteps--;
        }

        if (accumulator >= TIME_STEP) {
            accumulator = accumulator % TIME_STEP;
        }
    }

    @Override
    protected void process(int entityId) {
        PhysicsComponent phys = mPhysics.get(entityId);
        TransformComponent t = mTransform.get(entityId);

        if (phys.body != null && phys.body.isAwake()) {
            Vector2 pos = phys.body.getPosition();
            t.x = pos.x * PPM;
            t.y = pos.y * PPM;
            t.rotation = phys.body.getAngle() * MathUtils.radiansToDegrees;
        }
    }
}