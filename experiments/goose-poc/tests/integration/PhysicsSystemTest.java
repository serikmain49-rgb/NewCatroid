import com.artemis.World;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.fast2d.systems.Fast2DPhysicsSystem;

public final class PhysicsSystemTest {
    static final class Harness extends Fast2DPhysicsSystem {
        Harness(com.badlogic.gdx.physics.box2d.World physics, World world) { super(physics); this.world = world; }
        void run() { begin(); }
    }
    public static void main(String[] args) {
        if (!GooseMovement.update(0,0,0,0,0,0,0,0,new float[4])) throw new AssertionError("Goose unavailable");
        World world = new World();
        com.badlogic.gdx.physics.box2d.World physics = new com.badlogic.gdx.physics.box2d.World();
        Harness h = new Harness(physics, world);
        float accumulator = 0f, timeStep = 1f/60f;
        int total = 0;
        java.util.Random random = new java.util.Random(817);
        for (int i = 0; i < 5000; i++) {
            world.delta = random.nextFloat() * .12f;
            accumulator += world.delta;
            int steps = 0;
            while (accumulator >= timeStep && steps < 3) { accumulator -= timeStep; steps++; }
            if (accumulator >= timeStep) accumulator %= timeStep;
            total += steps;
            h.run();
            if (total != physics.calls) throw new AssertionError("physics step count mismatch at " + i);
        }
        System.out.println("PASS: Fast2D physics scheduler compared over 5000 frames");
    }
}
