package org.catrobat.catroid.fast2d.systems;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.Arrays;
import org.catrobat.catroid.fast2d.GooseMovement;

import org.catrobat.catroid.fast2d.components.TextureComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;

public class Fast2DRenderSystem extends BaseEntitySystem {

    protected ComponentMapper<TransformComponent> mTransform;
    protected ComponentMapper<TextureComponent> mTexture;

    private final SpriteBatch batch;
    private OrthographicCamera camera;
    private boolean needSort = false;

    private float camX, camY, camHalfW, camHalfH;
    private float[] visibility = new float[0];

    public Fast2DRenderSystem(SpriteBatch batch) {
        super(Aspect.all(TransformComponent.class, TextureComponent.class));
        this.batch = batch;
    }

    @Override
    protected void initialize() {
        getSubscription().addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) { needSort = true; }
            @Override
            public void removed(IntBag entities) { needSort = true; }
        });
    }

    public void setCamera(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void requestSort() {
        needSort = true;
    }

    @Override
    protected void processSystem() {
        IntBag activeEntities = getSubscription().getEntities();
        int[] ids = activeEntities.getData();
        int size = activeEntities.size();

        if (needSort && size > 1) {
            insertionSort(ids, size);
            needSort = false;
        }

        if (camera != null) {
            camX = camera.position.x;
            camY = camera.position.y;
            camHalfW = (camera.viewportWidth * camera.zoom) * 0.5f;
            camHalfH = (camera.viewportHeight * camera.zoom) * 0.5f;
            batch.setProjectionMatrix(camera.combined);
        }

        boolean culled = false;
        if (size > 0 && GooseMovement.isAvailable() && size <= Integer.MAX_VALUE / 5) {
            int needed = size * 5;
            if (visibility.length < needed) visibility = Arrays.copyOf(visibility, needed);
            for (int i = 0; i < size; i++) {
                TextureComponent tex = mTexture.get(ids[i]);
                TransformComponent transform = mTransform.get(ids[i]);
                int offset = i * 5;
                visibility[offset] = transform.x;
                visibility[offset + 1] = transform.y;
                visibility[offset + 2] = (tex.width * transform.scaleX) * 0.5f;
                visibility[offset + 3] = (tex.height * transform.scaleY) * 0.5f;
            }
            culled = GooseMovement.visibility(visibility, size, camX, camY, camHalfW, camHalfH);
        }
        batch.begin();
        for (int i = 0; i < size; i++) {
            if (!culled || visibility[i * 5 + 4] != 0f) renderEntity(ids[i], culled);
        }
        batch.end();
    }

    private void renderEntity(int entityId, boolean alreadyCulled) {
        TextureComponent tex = mTexture.get(entityId);
        if (tex.region == null) return;

        TransformComponent t = mTransform.get(entityId);

        float halfW = (tex.width * t.scaleX) * 0.5f;
        float halfH = (tex.height * t.scaleY) * 0.5f;

        if (!alreadyCulled && (t.x + halfW < camX - camHalfW || t.x - halfW > camX + camHalfW ||
                t.y + halfH < camY - camHalfH || t.y - halfH > camY + camHalfH)) {
            return;
        }

        batch.setColor(tex.color);
        batch.draw(
                tex.region,
                t.x - tex.originX, t.y - tex.originY,
                tex.originX, tex.originY,
                tex.width, tex.height,
                t.scaleX, t.scaleY,
                t.rotation
        );
    }

    private void insertionSort(int[] arr, int size) {
        for (int i = 1; i < size; i++) {
            int key = arr[i];
            float keyZ = mTransform.get(key).z;
            int j = i - 1;
            while (j >= 0 && mTransform.get(arr[j]).z > keyZ) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }
}