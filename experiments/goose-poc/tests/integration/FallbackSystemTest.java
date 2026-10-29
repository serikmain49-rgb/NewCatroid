import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.fast2d.components.MovementComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;
import org.catrobat.catroid.fast2d.components.TextureComponent;
import org.catrobat.catroid.fast2d.systems.Fast2DMovementSystem;
import org.catrobat.catroid.fast2d.systems.Fast2DRenderSystem;

public final class FallbackSystemTest {
    static final class Movement extends Fast2DMovementSystem {
        Movement(World world, TransformComponent t, MovementComponent m) {
            this.world = world;
            mTransform = new ComponentMapper<>(t); mMovement = new ComponentMapper<>(m);
        }
        void run() { begin(); process(0); end(); }
    }
    static final class Render extends Fast2DRenderSystem {
        Render(SpriteBatch batch, TransformComponent[] ts, TextureComponent[] textures) {
            super(batch); mTransform = new ComponentMapper<>(ts); mTexture = new ComponentMapper<>(textures);
            initialize(); getSubscription().setEntities(new int[] { 0, 1 });
        }
        void run() { processSystem(); }
    }
    public static void main(String[] args) {
        if (GooseMovement.isAvailable()) throw new AssertionError("Goose must be disabled");
        World world = new World(); world.delta = .5f;
        TransformComponent a = new TransformComponent(), b = new TransformComponent();
        MovementComponent m = new MovementComponent(); m.velocityX = 2; m.velocityY = 4; m.gravityY = -2;
        new Movement(world, a, m).run();
        if (a.x != 1 || a.y != 1.5f || m.velocityY != 3) throw new AssertionError("Java movement fallback failed");
        b.x = 100;
        TextureComponent ta = new TextureComponent(), tb = new TextureComponent();
        ta.region = new TextureRegion(0); tb.region = new TextureRegion(1);
        ta.width = ta.height = tb.width = tb.height = 2;
        SpriteBatch batch = new SpriteBatch();
        Render render = new Render(batch, new TransformComponent[] {a,b}, new TextureComponent[] {ta,tb});
        OrthographicCamera camera = new OrthographicCamera(); camera.viewportWidth = camera.viewportHeight = 20;
        render.setCamera(camera); render.run();
        if (!batch.drawn.toString().equals("[0]")) throw new AssertionError("Java visibility fallback failed: " + batch.drawn);
        System.out.println("PASS: original Java movement and visibility fallback");
    }
}
