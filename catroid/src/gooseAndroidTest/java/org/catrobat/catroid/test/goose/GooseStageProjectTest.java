package org.catrobat.catroid.test.goose;

import android.app.Instrumentation;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import com.badlogic.gdx.Gdx;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.bricks.WaitBrick;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.stage.StageActivity;
import org.catrobat.catroid.stage.TestResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Runs the repository's existing Change X by N visual-block project on a real stage. */
@RunWith(AndroidJUnit4.class)
public class GooseStageProjectTest {
    // The StageActivity requests these at startup. Grant them only to the
    // isolated instrumentation app so a system dialog cannot pause the scene.
    @Rule public GrantPermissionRule permissions = GrantPermissionRule.grant(
            android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_EXTERNAL_STORAGE);
    @Rule public ActivityTestRule<StageActivity> stage = new ActivityTestRule<>(StageActivity.class, true, false);

    @Test public void runsExistingProject() throws Exception {
        File dir = GooseProjectRoundTrip.unpackFixture("catrobatTests/bricks/motion/testChangeXby.catrobat");
        try {
            if (ProjectManager.getInstance() == null)
                new ProjectManager(InstrumentationRegistry.getInstrumentation().getTargetContext());
            ProjectManager manager = ProjectManager.getInstance();
            manager.resetProjectManager();
            manager.loadProject(dir, InstrumentationRegistry.getInstrumentation().getTargetContext());
            manager.setCurrentProject(manager.getCurrentProject());
            manager.setStartScene(manager.getCurrentProject().getDefaultScene());
            Sprite fixture = null;
            for (Sprite sprite : manager.getCurrentProject().getDefaultScene().getSpriteList())
                if ("Racoon-4".equals(sprite.getName())) fixture = sprite;
            assertNotNull("Fixture sprite missing", fixture);
            // Keep the scene alive briefly while checking the actual render thread.
            fixture.getScriptList().get(0).addBrick(0, new WaitBrick(1500));
            stage.launchActivity(null);
            long nativeDeadline = System.currentTimeMillis() + 15000;
            while (Gdx.app == null && System.currentTimeMillis() < nativeDeadline) Thread.sleep(25);
            assertNotNull("Game runtime did not initialize", Gdx.app);
            CountDownLatch checked = new CountDownLatch(1);
            AtomicInteger resultOnRenderThread = new AtomicInteger(-2);
            Gdx.app.postRunnable(() -> {
                try {
                    if (GooseMovement.prepareGameThread())
                        resultOnRenderThread.set(GooseMovement.hitTest(0, 0, 0, 0, false, 1, 1));
                    else resultOnRenderThread.set(-1);
                } finally { checked.countDown(); }
            });
            assertTrue("Render thread did not respond", checked.await(10, TimeUnit.SECONDS));
            assertEquals("Goose unavailable on the game thread", 1, resultOnRenderThread.get());
            long deadline = System.currentTimeMillis() + 30000;
            while (!stage.getActivity().isFinishing() && System.currentTimeMillis() < deadline)
                Thread.sleep(25);
            if (!stage.getActivity().isFinishing()) fail("Project did not finish within 30 seconds");
            Instrumentation.ActivityResult result = stage.getActivityResult();
            assertNotNull("No stage result", result);
            assertEquals("Fixture reported a failed block assertion", TestResult.STAGE_ACTIVITY_TEST_SUCCESS, result.getResultCode());
        } finally {
            GooseProjectRoundTrip.delete(dir);
        }
    }
}
