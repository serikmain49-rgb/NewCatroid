import com.artemis.ComponentMapper;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.fast2d.components.TransformComponent;
import org.catrobat.catroid.fast2d.components.TextureComponent;
import org.catrobat.catroid.fast2d.systems.Fast2DRenderSystem;

public final class RenderSystemTest {
    static final class Harness extends Fast2DRenderSystem {
        Harness(SpriteBatch batch, TransformComponent[] transforms, TextureComponent[] textures) {
            super(batch);
            mTransform = new ComponentMapper<>(transforms);
            mTexture = new ComponentMapper<>(textures);
            initialize();
            getSubscription().setEntities(new int[] {0,1,2,3});
        }
        void run() { processSystem(); }
    }
    public static void main(String[] args) throws Exception {
        if (!GooseMovement.update(0,0,0,0,0,0,0,0,new float[4])) throw new AssertionError("Goose unavailable");
        TransformComponent[] ts = new TransformComponent[4];
        TextureComponent[] tx = new TextureComponent[4];
        for (int i=0; i<4; i++) {
            ts[i] = new TransformComponent(); tx[i] = new TextureComponent();
            tx[i].width = tx[i].height = 2f; tx[i].region = new TextureRegion(i);
            ts[i].z = 3-i;
        }
        ts[1].x = 100f; // outside camera
        ts[2].x = 11f;  // exactly touches camera edge, remains visible
        tx[3].region = null;
        SpriteBatch batch = new SpriteBatch();
        Harness system = new Harness(batch, ts, tx);
        OrthographicCamera camera = new OrthographicCamera(); camera.viewportWidth = camera.viewportHeight = 20f;
        system.setCamera(camera); system.run();
        if (!batch.drawn.toString().equals("[2, 0]")) throw new AssertionError("Unexpected render order/visibility " + batch.drawn);
        System.out.println("PASS: Fast2D visibility, edge and render order");
    }
}
