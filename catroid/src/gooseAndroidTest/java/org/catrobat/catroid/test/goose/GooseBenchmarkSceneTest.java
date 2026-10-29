package org.catrobat.catroid.test.goose;

import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.badlogic.gdx.Gdx;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.catrobat.catroid.BuildConfig;
import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.bricks.FinishStageBrick;
import org.catrobat.catroid.content.bricks.WaitBrick;
import org.catrobat.catroid.fast2d.FastTwoDManager;
import org.catrobat.catroid.stage.StageActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Reproducible rendered Fast2D workload; each invocation is a fresh process. */
@RunWith(AndroidJUnit4.class)
public class GooseBenchmarkSceneTest {
    @Rule public ActivityTestRule<StageActivity> stage = new ActivityTestRule<>(StageActivity.class, true, false);

    @Test public void renderScene() throws Exception {
        assertTrue("Enable frame logging with -PgooseBenchmark=true", BuildConfig.FEATURE_GOOSE_BENCHMARK);
        String input = InstrumentationRegistry.getArguments().getString("scenarioCount", "100");
        int count = Integer.parseInt(input);
        assertTrue("Supported scene sizes: 10, 100, 1000", count == 10 || count == 100 || count == 1000);
        File dir = GooseProjectRoundTrip.unpackFixture("catrobatTests/bricks/motion/testChangeXby.catrobat");
        try {
            if (ProjectManager.getInstance() == null)
                new ProjectManager(InstrumentationRegistry.getInstrumentation().getTargetContext());
            ProjectManager manager = ProjectManager.getInstance();
            manager.resetProjectManager();
            manager.loadProject(dir, InstrumentationRegistry.getInstrumentation().getTargetContext());
            Project project = manager.getCurrentProject();
            manager.setCurrentProject(project);
            manager.setStartScene(project.getDefaultScene());
            Sprite sprite = null;
            for (Sprite candidate : project.getDefaultScene().getSpriteList())
                if ("Racoon-4".equals(candidate.getName())) sprite = candidate;
            assertTrue("Fixture sprite missing", sprite != null);
            Script script = sprite.getScriptList().get(0);
            script.getBrickList().clear();
            script.addBrick(new WaitBrick(90000));
            script.addBrick(new FinishStageBrick());
            File image = new File(project.getFilesDir(), "goose-benchmark.png");
            Files.copy(new File(dir, "Szene/images/Racoon-4.png").toPath(), image.toPath(), StandardCopyOption.REPLACE_EXISTING);
            stage.launchActivity(null);

            long deadline = SystemClock.elapsedRealtime() + 30000;
            while ((Gdx.app == null || stage.getActivity().stageListener == null)
                    && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25);
            assertTrue("Game loop did not initialize", Gdx.app != null && stage.getActivity().stageListener != null);
            CountDownLatch ready = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Runnable setup = new Runnable() {
                @Override public void run() {
                    try {
                        FastTwoDManager fast = stage.getActivity().stageListener.fastTwoDManager;
                        if (fast == null) { Gdx.app.postRunnable(this); return; }
                        for (int i = 0; i < count; i++) {
                            String id = "bench-" + i;
                            fast.createEntity(id);
                            fast.setPosition(id, (i % 40) * 18f - 350f, (i / 40) * 18f - 220f);
                            fast.setTexture(id, image.getAbsolutePath());
                            fast.setScale(id, .1f, .1f);
                            fast.setVelocity(id, .25f, .125f);
                        }
                        Gdx.app.postRunnable(() -> {
                            try {
                                if (!"goose-benchmark.png".equals(fast.getTextureName("bench-" + (count - 1))))
                                    throw new AssertionError("Fast2D entities were not initialized");
                            } catch (Throwable error) { failure.set(error); }
                            finally { ready.countDown(); }
                        });
                    } catch (Throwable error) { failure.set(error); ready.countDown(); }
                }
            };
            Gdx.app.postRunnable(setup);
            assertTrue("Fast2D scene setup timed out", ready.await(30, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
            String backend = BuildConfig.FEATURE_GOOSE_FAST2D ? "goose" : "java";
            Debug.MemoryInfo memory = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memory);
            Log.i("GOOSE_SCENARIO", "phase=start backend=" + backend + " objects=" + count
                    + " pss_kb=" + memory.getTotalPss() + " native_heap_bytes=" + Debug.getNativeHeapAllocatedSize());
            // The first logged 10-second frame window may include setup. Discard it.
            Thread.sleep(23000);
            assertFalse("Scene ended before measurement", stage.getActivity().isFinishing());
            Debug.getMemoryInfo(memory);
            Log.i("GOOSE_SCENARIO", "phase=end backend=" + backend + " objects=" + count
                    + " pss_kb=" + memory.getTotalPss() + " native_heap_bytes=" + Debug.getNativeHeapAllocatedSize());
        } finally {
            stage.finishActivity();
            GooseProjectRoundTrip.delete(dir);
        }
    }
}
