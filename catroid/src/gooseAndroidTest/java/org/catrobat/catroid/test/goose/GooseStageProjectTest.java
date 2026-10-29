package org.catrobat.catroid.test.goose;

import android.app.Instrumentation;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import java.io.File;
import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.stage.StageActivity;
import org.catrobat.catroid.stage.TestResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/** Runs the repository's existing Change X by N visual-block project on a real stage. */
@RunWith(AndroidJUnit4.class)
public class GooseStageProjectTest {
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
            stage.launchActivity(null);
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
