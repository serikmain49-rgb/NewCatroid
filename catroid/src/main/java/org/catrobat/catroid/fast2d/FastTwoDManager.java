package org.catrobat.catroid.fast2d;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Disposable;

import org.catrobat.catroid.fast2d.components.MovementComponent;
import org.catrobat.catroid.fast2d.components.PhysicsComponent;
import org.catrobat.catroid.fast2d.components.TextureComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;
import org.catrobat.catroid.fast2d.systems.Fast2DMovementSystem;
import org.catrobat.catroid.fast2d.systems.Fast2DPhysicsSystem;
import org.catrobat.catroid.fast2d.systems.Fast2DRenderSystem;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FastTwoDManager implements Disposable {
    private World world;
    private Fast2DRenderSystem renderSystem;
    private Fast2DMovementSystem movementSystem;

    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<TextureComponent> mTexture;
    private ComponentMapper<MovementComponent> mMovement;

    private com.badlogic.gdx.physics.box2d.World physicsWorld;
    private ComponentMapper<PhysicsComponent> mPhysics;

    private OrthographicCamera camera;
    private final Map<String, Integer> entities = new HashMap<>();
    private final Map<String, Texture> textureCache = new HashMap<>();

    public void init(SpriteBatch batch) {
        physicsWorld = new com.badlogic.gdx.physics.box2d.World(new Vector2(0, -9.8f), true);

        renderSystem = new Fast2DRenderSystem(batch);
        movementSystem = new Fast2DMovementSystem();
        Fast2DPhysicsSystem physicsSystem = new Fast2DPhysicsSystem(physicsWorld);

        WorldConfiguration config = new WorldConfigurationBuilder()
                .with(movementSystem, physicsSystem, renderSystem)
                .build();

        world = new World(config);

        mTransform = world.getMapper(TransformComponent.class);
        mTexture = world.getMapper(TextureComponent.class);
        mMovement = world.getMapper(MovementComponent.class);
        mPhysics = world.getMapper(PhysicsComponent.class);

        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 0, 0);
        camera.update();
    }

    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.position.set(0, 0, 0);
        camera.update();
    }

    public void updateAndRender(float delta) {
        renderSystem.setCamera(camera);
        world.setDelta(delta);
        world.process();
    }

    private int getOrCreateEntity(String entityId) {
        Integer entity = entities.get(entityId);
        if (entity == null) {
            int newEntity = world.create();
            mTransform.create(newEntity);
            entities.put(entityId, newEntity);
            return newEntity;
        }
        return entity;
    }

    private Texture getOrLoadTexture(String absolutePath) {
        if (textureCache.containsKey(absolutePath)) return textureCache.get(absolutePath);
        try {
            Texture texture = new Texture(Gdx.files.absolute(absolutePath), true);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            textureCache.put(absolutePath, texture);
            return texture;
        } catch (Exception e) {
            Gdx.app.error("Fast2D", "Failed to load texture: " + absolutePath, e);
        }
        return null;
    }

    private void rebuildFixture(int e) {
        if (!mPhysics.has(e)) return;
        PhysicsComponent phys = mPhysics.get(e);
        if (phys.body == null) return;

        TransformComponent t = mTransform.get(e);
        TextureComponent tex = mTexture.has(e) ? mTexture.get(e) : null;

        com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Fixture> fixtures = phys.body.getFixtureList();
        while (fixtures.size > 0) {
            phys.body.destroyFixture(fixtures.first());
        }

        float w = (tex != null && tex.region != null) ? Math.abs(tex.width * t.scaleX) / 2f : 25f;
        float h = (tex != null && tex.region != null) ? Math.abs(tex.height * t.scaleY) / 2f : 25f;

        if (w < 0.1f) w = 0.1f;
        if (h < 0.1f) h = 0.1f;

        com.badlogic.gdx.physics.box2d.Shape box2dShape;
        if ("CIRCLE".equalsIgnoreCase(phys.shapeType)) {
            com.badlogic.gdx.physics.box2d.CircleShape circle = new com.badlogic.gdx.physics.box2d.CircleShape();
            circle.setRadius(Math.max(w, h) / Fast2DPhysicsSystem.PPM);
            box2dShape = circle;
        } else {
            com.badlogic.gdx.physics.box2d.PolygonShape poly = new com.badlogic.gdx.physics.box2d.PolygonShape();
            poly.setAsBox(w / Fast2DPhysicsSystem.PPM, h / Fast2DPhysicsSystem.PPM);
            box2dShape = poly;
        }

        com.badlogic.gdx.physics.box2d.FixtureDef fdef = new com.badlogic.gdx.physics.box2d.FixtureDef();
        fdef.shape = box2dShape;
        fdef.density = phys.density;
        fdef.friction = phys.friction;
        fdef.restitution = phys.bounce;

        phys.body.createFixture(fdef);
        box2dShape.dispose();

        if (phys.isDynamic) {
            phys.body.resetMassData();
        }
        phys.body.setAwake(true);
    }

    public void createEntity(final String id) {
        Gdx.app.postRunnable(() -> getOrCreateEntity(id));
    }

    public void destroyEntity(final String id) {
        Gdx.app.postRunnable(() -> {
            Integer e = entities.remove(id);
            if (e != null) world.delete(e);
        });
    }

    public void setPosition(final String id, final float x, final float y) {
        Gdx.app.postRunnable(() -> {
            int e = getOrCreateEntity(id);
            TransformComponent t = mTransform.get(e);
            t.x = x;
            t.y = y;

            if (mPhysics.has(e) && mPhysics.get(e).body != null) {
                mPhysics.get(e).body.setTransform(x / Fast2DPhysicsSystem.PPM, y / Fast2DPhysicsSystem.PPM, mPhysics.get(e).body.getAngle());
                mPhysics.get(e).body.setAwake(true);
            }
        });
    }

    public void setRotation(final String id, final float angle) {
        Gdx.app.postRunnable(() -> {
            int e = getOrCreateEntity(id);
            mTransform.get(e).rotation = angle;
            if (mPhysics.has(e) && mPhysics.get(e).body != null) {
                com.badlogic.gdx.physics.box2d.Body b = mPhysics.get(e).body;
                b.setTransform(b.getPosition(), angle * com.badlogic.gdx.math.MathUtils.degreesToRadians);
                b.setAwake(true);
            }
        });
    }

    public void setScale(final String id, final float sx, final float sy) {
        Gdx.app.postRunnable(() -> {
            int e = getOrCreateEntity(id);
            mTransform.get(e).scaleX = sx;
            mTransform.get(e).scaleY = sy;
            rebuildFixture(e);
        });
    }

    public void setZIndex(final String id, final float z) {
        Gdx.app.postRunnable(() -> {
            mTransform.get(getOrCreateEntity(id)).z = z;
            renderSystem.requestSort();
        });
    }

    public void setTexture(final String id, final String absolutePath) {
        Gdx.app.postRunnable(() -> {
            int e = getOrCreateEntity(id);
            Texture texture = getOrLoadTexture(absolutePath);
            if (texture == null) return;

            TextureComponent tex = mTexture.create(e);
            tex.region = new TextureRegion(texture);

            int lastSlashIndex = absolutePath.lastIndexOf('/');
            tex.textureName = lastSlashIndex >= 0 ? absolutePath.substring(lastSlashIndex + 1) : absolutePath;

            tex.width = texture.getWidth();
            tex.height = texture.getHeight();
            tex.originX = tex.width / 2f;
            tex.originY = tex.height / 2f;

            rebuildFixture(e);
        });
    }

    public void setColor(final String id, final float r, final float g, final float b, final float a) {
        Gdx.app.postRunnable(() -> {
            TextureComponent tex = mTexture.create(getOrCreateEntity(id));
            tex.color.set(r / 255f, g / 255f, b / 255f, a / 100f);
        });
    }

    public void setVelocity(final String id, final float vx, final float vy) {
        Gdx.app.postRunnable(() -> {
            MovementComponent m = mMovement.create(getOrCreateEntity(id));
            m.velocityX = vx; m.velocityY = vy;
        });
    }

    public void setAngularVelocity(final String id, final float angVel) {
        Gdx.app.postRunnable(() -> {
            mMovement.create(getOrCreateEntity(id)).angularVelocity = angVel;
        });
    }

    public void makePhysicsBody(final String id, final boolean isDynamic, final String shape, final float density, final float friction, final float bounce) {
        Gdx.app.postRunnable(() -> {
            int e = getOrCreateEntity(id);
            TransformComponent t = mTransform.get(e);

            if (mPhysics.has(e) && mPhysics.get(e).body != null) {
                physicsWorld.destroyBody(mPhysics.get(e).body);
            }

            com.badlogic.gdx.physics.box2d.BodyDef bdef = new com.badlogic.gdx.physics.box2d.BodyDef();
            bdef.type = isDynamic ? com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody : com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody;
            bdef.position.set(t.x / Fast2DPhysicsSystem.PPM, t.y / Fast2DPhysicsSystem.PPM);
            bdef.angle = t.rotation * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            bdef.awake = true;
            bdef.allowSleep = true;

            com.badlogic.gdx.physics.box2d.Body body = physicsWorld.createBody(bdef);

            PhysicsComponent phys = mPhysics.create(e);
            phys.body = body;
            phys.isDynamic = isDynamic;
            phys.shapeType = shape;
            phys.density = density;
            phys.friction = friction;
            phys.bounce = bounce;

            rebuildFixture(e);
        });
    }

    public void applyForce(final String id, final float forceX, final float forceY) {
        Gdx.app.postRunnable(() -> {
            Integer e = entities.get(id);
            if (e != null && mPhysics.has(e) && mPhysics.get(e).body != null) {
                mPhysics.get(e).body.applyForceToCenter(forceX, forceY, true);
            }
        });
    }

    public void applyImpulse(final String id, final float impX, final float impY) {
        Gdx.app.postRunnable(() -> {
            Integer e = entities.get(id);
            if (e != null && mPhysics.has(e) && mPhysics.get(e).body != null) {
                Body b = mPhysics.get(e).body;
                Vector2 center = b.getWorldCenter();
                b.applyLinearImpulse(impX, impY, center.x, center.y, true);
            }
        });
    }

    public void setPhysicsVelocity(final String id, final float vx, final float vy) {
        Gdx.app.postRunnable(() -> {
            Integer e = entities.get(id);
            if (e != null && mPhysics.has(e) && mPhysics.get(e).body != null) {
                mPhysics.get(e).body.setLinearVelocity(vx, vy);
            }
        });
    }

    public void setCamera(final float x, final float y, final float zoom) {
        Gdx.app.postRunnable(() -> {
            camera.position.set(x, y, 0);
            camera.zoom = zoom;
            camera.update();
        });
    }

    public void setGravity(final float x, final float y) {
        Gdx.app.postRunnable(() -> {
            if (physicsWorld != null) {
                physicsWorld.setGravity(new Vector2(x, y));
            }
        });
    }

    public void setCollisionFilter(final String id, final boolean isSensor, final int groupIndex) {
        Gdx.app.postRunnable(() -> {
            try {
                int e = getOrCreateEntity(id);
                if (mPhysics.has(e) && mPhysics.get(e).body != null) {
                    com.badlogic.gdx.physics.box2d.Body body = mPhysics.get(e).body;
                    com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Fixture> fixtures = body.getFixtureList();

                    for (int i = 0; i < fixtures.size; i++) {
                        com.badlogic.gdx.physics.box2d.Fixture f = fixtures.get(i);
                        f.setSensor(isSensor);

                        com.badlogic.gdx.physics.box2d.Filter filter = f.getFilterData();
                        filter.groupIndex = (short) groupIndex;
                        f.setFilterData(filter);
                    }
                    body.setAwake(true);
                }
            } catch (Exception ex) {
                Gdx.app.error("Fast2D_Physics", "Failed to set collision filter for: " + id, ex);
            }
        });
    }

    private final Vector3 tempVec = new Vector3();

    public boolean isEntityTouched(String id, int pointer) {
        Integer e = entities.get(id);
        if (e == null || !Gdx.input.isTouched(pointer)) return false;

        camera.update();

        tempVec.set(Gdx.input.getX(pointer), Gdx.input.getY(pointer), 0);
        camera.unproject(tempVec);

        if (!mTransform.has(e) || !mTexture.has(e)) return false;

        TransformComponent t = mTransform.get(e);
        TextureComponent tex = mTexture.get(e);

        if (tex.region == null) return false;

        float dx = tempVec.x - t.x;
        float dy = tempVec.y - t.y;

        boolean rotated = t.rotation != 0;
        float cos = 0f, sin = 0f;
        if (rotated) {
            cos = com.badlogic.gdx.math.MathUtils.cosDeg(-t.rotation);
            sin = com.badlogic.gdx.math.MathUtils.sinDeg(-t.rotation);
        }

        float halfW = Math.abs(tex.width * t.scaleX) * 0.5f;
        float halfH = Math.abs(tex.height * t.scaleY) * 0.5f;
        int gooseHit = GooseMovement.hitTest(dx, dy, cos, sin, rotated, halfW, halfH);
        if (gooseHit >= 0) return gooseHit == 1;

        if (rotated) {
            float rx = dx * cos - dy * sin;
            float ry = dx * sin + dy * cos;
            dx = rx;
            dy = ry;
        }
        return dx >= -halfW && dx <= halfW && dy >= -halfH && dy <= halfH;
    }

    public float getPositionX(String id) { Integer e = entities.get(id); return e != null ? mTransform.get(e).x : 0f; }
    public float getPositionY(String id) { Integer e = entities.get(id); return e != null ? mTransform.get(e).y : 0f; }
    public float getRotation(String id) { Integer e = entities.get(id); return e != null ? mTransform.get(e).rotation : 0f; }
    public float getScaleX(String id) { Integer e = entities.get(id); return e != null ? mTransform.get(e).scaleX : 1f; }
    public float getScaleY(String id) { Integer e = entities.get(id); return e != null ? mTransform.get(e).scaleY : 1f; }
    public String getTextureName(String id) { Integer e = entities.get(id); return (e != null && mTexture.has(e)) ? mTexture.get(e).textureName : ""; }
    public float getColorR(String id) { Integer e = entities.get(id); return (e != null && mTexture.has(e)) ? mTexture.get(e).color.r * 255f : 255f; }
    public float getColorG(String id) { Integer e = entities.get(id); return (e != null && mTexture.has(e)) ? mTexture.get(e).color.g * 255f : 255f; }
    public float getColorB(String id) { Integer e = entities.get(id); return (e != null && mTexture.has(e)) ? mTexture.get(e).color.b * 255f : 255f; }
    public float getAlpha(String id) { Integer e = entities.get(id); return (e != null && mTexture.has(e)) ? mTexture.get(e).color.a * 100f : 100f; }

    public float getCamX() { return camera.position.x; }
    public float getCamY() { return camera.position.y; }
    public float getCamZoom() { return camera.zoom; }

    public void clearScene() {
        for (int entityId : entities.values()) {
            world.delete(entityId);
        }
        entities.clear();
    }

    @Override
    public void dispose() {
        clearScene();
        for (Texture tex : textureCache.values()) tex.dispose();
        textureCache.clear();
        world.dispose();
        physicsWorld.dispose();
    }
}
