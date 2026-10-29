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
        void run() { begin(); process(0); end(); }
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
        System.out.println("PASS: Fast2D movement system with Goose enabled");
    }
}
