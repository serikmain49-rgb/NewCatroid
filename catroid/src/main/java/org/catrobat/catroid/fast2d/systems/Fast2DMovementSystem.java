package org.catrobat.catroid.fast2d.systems;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import java.util.Arrays;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.fast2d.components.MovementComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;

public class Fast2DMovementSystem extends IteratingSystem {
    private static final int WIDTH = 7;
    private float[] records = new float[0];
    private TransformComponent[] transforms = new TransformComponent[0];
    private MovementComponent[] movements = new MovementComponent[0];
    private int count;
    private boolean batchEnabled;
    protected ComponentMapper<TransformComponent> mTransform;
    protected ComponentMapper<MovementComponent> mMovement;

    public Fast2DMovementSystem() {
        super(Aspect.all(TransformComponent.class, MovementComponent.class));
    }

    @Override protected void begin() {
        count = 0;
        batchEnabled = GooseMovement.isAvailable();
    }

    @Override protected void process(int entityId) {
        TransformComponent t = mTransform.get(entityId);
        MovementComponent m = mMovement.get(entityId);
        if (!batchEnabled) {
            updateJava(t, m, world.getDelta());
            return;
        }
        if (count == transforms.length) {
            int capacity = Math.max(16, count * 2);
            transforms = Arrays.copyOf(transforms, capacity);
            movements = Arrays.copyOf(movements, capacity);
            records = Arrays.copyOf(records, capacity * WIDTH);
        }
        int i = count * WIDTH;
        transforms[count] = t;
        movements[count] = m;
        records[i] = t.x;
        records[i + 1] = t.y;
        records[i + 2] = t.rotation;
        records[i + 3] = m.velocityX;
        records[i + 4] = m.velocityY;
        records[i + 5] = m.gravityY;
        records[i + 6] = m.angularVelocity;
        count++;
    }

    @Override protected void end() {
        if (!batchEnabled || count == 0) return;
        float delta = world.getDelta();
        boolean success = GooseMovement.updateBatch(records, count, delta);
        for (int entity = 0; entity < count; entity++) {
            TransformComponent t = transforms[entity];
            MovementComponent m = movements[entity];
            if (success) {
                int i = entity * WIDTH;
                t.x = records[i];
                t.y = records[i + 1];
                t.rotation = records[i + 2];
                m.velocityY = records[i + 4];
            } else {
                updateJava(t, m, delta);
            }
            transforms[entity] = null;
            movements[entity] = null;
        }
    }

    private static void updateJava(TransformComponent t, MovementComponent m, float delta) {
        m.velocityY += m.gravityY * delta;
        t.x += m.velocityX * delta;
        t.y += m.velocityY * delta;
        t.rotation += m.angularVelocity * delta;
    }
}
