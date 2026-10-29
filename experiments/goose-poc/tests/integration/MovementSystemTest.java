import com.artemis.ComponentMapper;
import com.artemis.World;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.fast2d.components.MovementComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;
import org.catrobat.catroid.fast2d.systems.Fast2DMovementSystem;

public final class MovementSystemTest {
    static final class Harness extends Fast2DMovementSystem {
        Harness(World world, TransformComponent t, MovementComponent m) {
            this.world = world;
            mTransform = new ComponentMapper<>(t);
            mMovement = new ComponentMapper<>(m);
        }
        Harness(World world, TransformComponent[] t, MovementComponent[] m) {
            this.world = world;
            mTransform = new ComponentMapper<>(t);
            mMovement = new ComponentMapper<>(m);
        }
        void run() { run(1); }
        void run(int count) { begin(); for (int i = 0; i < count; i++) process(i); end(); }
    }
    static void equal(float expected, float actual) {
        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual))
            throw new AssertionError("expected " + expected + " got " + actual);
    }
    public static void main(String[] args) throws Exception {
        if (!GooseMovement.update(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, new float[4]))
            throw new AssertionError("Goose bridge is unavailable");
        World world = new World(); world.delta = .125f;
        TransformComponent t = new TransformComponent(); t.x = 10f; t.y = -4f; t.rotation = 2f;
        MovementComponent m = new MovementComponent(); m.velocityX = 3f; m.velocityY = -5f;
        m.gravityY = -9.8f; m.angularVelocity = .75f;
        Harness h = new Harness(world, t, m);
        float expectedVy = m.velocityY + m.gravityY * world.delta;
        float expectedX = t.x + m.velocityX * world.delta;
        float expectedY = t.y + expectedVy * world.delta;
        float expectedRotation = t.rotation + m.angularVelocity * world.delta;
        h.run();
        equal(expectedVy, m.velocityY); equal(expectedX, t.x);
        equal(expectedY, t.y); equal(expectedRotation, t.rotation);
        int count = 33;
        TransformComponent[] transforms = new TransformComponent[count];
        MovementComponent[] movements = new MovementComponent[count];
        float[][] values = new float[count][4];
        for (int i = 0; i < count; i++) {
            transforms[i] = new TransformComponent();
            movements[i] = new MovementComponent();
            transforms[i].x = values[i][0] = i * .7f;
            transforms[i].y = values[i][1] = i * -.4f;
            transforms[i].rotation = values[i][2] = i;
            movements[i].velocityY = values[i][3] = i * .1f;
            movements[i].velocityX = 1f - i * .3f;
            movements[i].gravityY = -9.8f;
            movements[i].angularVelocity = i * .05f;
        }
        Harness batch = new Harness(world, transforms, movements);
        batch.run(0);
        for (int frame = 0; frame < 120; frame++) {
            world.delta = frame % 2 == 0 ? .016f : .033f;
            for (int i = 0; i < count; i++) {
                values[i][3] += movements[i].gravityY * world.delta;
                values[i][0] += movements[i].velocityX * world.delta;
                values[i][1] += values[i][3] * world.delta;
                values[i][2] += movements[i].angularVelocity * world.delta;
            }
            batch.run(count);
            for (int i = 0; i < count; i++) {
                equal(values[i][0], transforms[i].x); equal(values[i][1], transforms[i].y);
                equal(values[i][2], transforms[i].rotation); equal(values[i][3], movements[i].velocityY);
            }
        }
        System.out.println("PASS: Fast2D movement system, 33 objects and 120 frames");
    }
}
