package org.catrobat.catroid.fast2d.systems;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.fast2d.components.MovementComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;

public class Fast2DMovementSystem extends IteratingSystem {

    private final float[] gooseResult = new float[4];
    protected ComponentMapper<TransformComponent> mTransform;
    protected ComponentMapper<MovementComponent> mMovement;

    public Fast2DMovementSystem() {
        super(Aspect.all(TransformComponent.class, MovementComponent.class));
    }

    @Override
    protected void process(int entityId) {
        TransformComponent t = mTransform.get(entityId);
        MovementComponent m = mMovement.get(entityId);

        float delta = world.getDelta();

        if (GooseMovement.update(t.x, t.y, t.rotation, m.velocityX, m.velocityY,
                m.gravityY, m.angularVelocity, delta, gooseResult)) {
            m.velocityY = gooseResult[0];
            t.x = gooseResult[1];
            t.y = gooseResult[2];
            t.rotation = gooseResult[3];
            return;
        }
        m.velocityY += m.gravityY * delta;
        t.x += m.velocityX * delta;
        t.y += m.velocityY * delta;
        t.rotation += m.angularVelocity * delta;
    }
}