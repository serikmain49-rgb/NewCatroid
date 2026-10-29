package org.catrobat.catroid.test.goose;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import org.catrobat.catroid.fast2d.components.MovementComponent;
import org.catrobat.catroid.fast2d.components.PhysicsComponent;
import org.catrobat.catroid.fast2d.components.TransformComponent;
import org.catrobat.catroid.fast2d.systems.Fast2DMovementSystem;
import org.catrobat.catroid.fast2d.systems.Fast2DPhysicsSystem;
import static org.junit.Assert.assertEquals;

/** Production Artemis systems with real Box2D, without a graphics context. */
final class GooseFast2DIntegration {
    private static Body populatePhysics(com.badlogic.gdx.physics.box2d.World world) {
        BodyDef groundDef = new BodyDef();
        Body ground = world.createBody(groundDef);
        PolygonShape floor = new PolygonShape();
        floor.setAsBox(10f, .25f);
        ground.createFixture(floor, 0f);
        floor.dispose();

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(0, 2.5f);
        bodyDef.allowSleep = false;
        Body body = world.createBody(bodyDef);
        CircleShape ball = new CircleShape();
        ball.setRadius(.5f);
        body.createFixture(ball, 1f);
        ball.dispose();
        return body;
    }

    static void run() {
        Box2D.init();
        com.badlogic.gdx.physics.box2d.World physics =
                new com.badlogic.gdx.physics.box2d.World(new Vector2(0, -9.8f), true);
        com.badlogic.gdx.physics.box2d.World reference =
                new com.badlogic.gdx.physics.box2d.World(new Vector2(0, -9.8f), true);
        World ecs = new World(new WorldConfigurationBuilder()
                .with(new Fast2DMovementSystem(), new Fast2DPhysicsSystem(physics)).build());
        try {
            ComponentMapper<TransformComponent> transforms = ecs.getMapper(TransformComponent.class);
            ComponentMapper<MovementComponent> movements = ecs.getMapper(MovementComponent.class);
            ComponentMapper<PhysicsComponent> physicsComponents = ecs.getMapper(PhysicsComponent.class);
            int[] entities = new int[33];
            float[][] expected = new float[entities.length][7];
            for (int i = 0; i < entities.length; i++) {
                int id = entities[i] = ecs.create();
                TransformComponent transform = transforms.create(id);
                MovementComponent movement = movements.create(id);
                transform.x = expected[i][0] = i * 2.5f;
                transform.y = expected[i][1] = -i;
                transform.rotation = expected[i][2] = i * 0.25f;
                movement.velocityX = expected[i][3] = i * 0.1f;
                movement.velocityY = expected[i][4] = -i * 0.2f;
                movement.gravityY = expected[i][5] = -9.8f;
                movement.angularVelocity = expected[i][6] = 0.75f;
            }
            int physicsEntity = ecs.create();
            TransformComponent physicsTransform = transforms.create(physicsEntity);
            physicsComponents.create(physicsEntity).body = populatePhysics(physics);
            Body referenceBody = populatePhysics(reference);
            float accumulator = 0f, step = 1f / 60f;
            for (int frame = 0; frame < 120; frame++) {
                float delta = frame % 2 == 0 ? .016f : .033f;
                for (float[] e : expected) {
                    e[4] += e[5] * delta; e[0] += e[3] * delta;
                    e[1] += e[4] * delta; e[2] += e[6] * delta;
                }
                accumulator += delta;
                int steps = 0;
                while (accumulator >= step && steps++ < 3) {
                    reference.step(step, 4, 1); accumulator -= step;
                }
                if (accumulator >= step) accumulator %= step;
                ecs.setDelta(delta);
                ecs.process();
                for (int i = 0; i < entities.length; i++) {
                    TransformComponent actual = transforms.get(entities[i]);
                    MovementComponent movement = movements.get(entities[i]);
                    assertEquals(expected[i][0], actual.x, 0f);
                    assertEquals(expected[i][1], actual.y, 0f);
                    assertEquals(expected[i][2], actual.rotation, 0f);
                    assertEquals(expected[i][4], movement.velocityY, 0f);
                }
                assertEquals(referenceBody.getPosition().x * Fast2DPhysicsSystem.PPM, physicsTransform.x, 0f);
                assertEquals(referenceBody.getPosition().y * Fast2DPhysicsSystem.PPM, physicsTransform.y, 0f);
            }
        } finally {
            ecs.dispose();
            physics.dispose();
            reference.dispose();
        }
    }
}
