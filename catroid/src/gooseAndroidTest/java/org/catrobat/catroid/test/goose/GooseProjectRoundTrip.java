package org.catrobat.catroid.test.goose;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Scene;
import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.content.bricks.Fast2DSetVelocityBrick;
import org.catrobat.catroid.io.XstreamSerializer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Load and resave one existing Pocket Code project and a NewCatroid brick. */
final class GooseProjectRoundTrip {
    static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

    private static List<String> structure(Project project) {
        List<String> result = new ArrayList<>();
        for (Scene scene : project.getSceneList()) {
            result.add("scene:" + scene.getName());
            for (Sprite sprite : scene.getSpriteList()) {
                result.add("sprite:" + sprite.getName() + ":looks=" + sprite.getLookList().size());
                for (Script script : sprite.getScriptList()) {
                    result.add("script:" + script.getClass().getName());
                    for (Brick brick : script.getBrickList()) result.add("brick:" + brick.getClass().getName());
                }
            }
        }
        return result;
    }

    private static Sprite fixtureSprite(Project project) {
        for (Sprite sprite : project.getDefaultScene().getSpriteList()) {
            if ("Racoon-4".equals(sprite.getName())) return sprite;
        }
        throw new AssertionError("fixture sprite missing");
    }

    static File unpackFixture(String assetPath) throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        File dir = File.createTempFile("goose-project-", "", target.getCacheDir());
        assertTrue(dir.delete() && dir.mkdir());
        try {
            String root = dir.getCanonicalPath() + File.separator;
            try (ZipInputStream input = new ZipInputStream(tests.getAssets().open(assetPath))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = input.getNextEntry()) != null) {
                    File output = new File(dir, entry.getName());
                    if (!output.getCanonicalPath().startsWith(root)) throw new AssertionError("unsafe fixture path");
                    if (entry.isDirectory()) {
                        if (!output.isDirectory() && !output.mkdirs()) throw new AssertionError();
                    } else {
                        File parent = output.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) throw new AssertionError();
                        try (FileOutputStream stream = new FileOutputStream(output)) {
                            int count;
                            while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                        }
                    }
                    input.closeEntry();
                }
            }
            return dir;
        } catch (Exception | Error error) {
            delete(dir);
            throw error;
        }
    }

    static void run() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dir = unpackFixture("catrobatTests/bricks/motion/testChangeXby.catrobat");
        try {
            XstreamSerializer serializer = XstreamSerializer.getInstance();
            Project project = serializer.loadProject(dir, target);
            assertTrue(project.getSceneList().size() > 0);
            Sprite sprite = fixtureSprite(project);
            Script script = sprite.getScriptList().get(0);
            script.addBrick(new Fast2DSetVelocityBrick("goose-test", 3.5, -2.0));
            List<String> original = structure(project);
            // Save to the same temp directory without comparing against the old XML.
            assertTrue(new File(dir, "code.xml").renameTo(new File(dir, "original-code.xml")));
            assertTrue("project save failed", serializer.saveProject(project));
            Project loaded = serializer.loadProject(dir, target);
            assertEquals(original, structure(loaded));
            Script restoredScript = fixtureSprite(loaded).getScriptList().get(0);
            Brick last = restoredScript.getBrickList().get(restoredScript.getBrickList().size() - 1);
            assertTrue(last instanceof Fast2DSetVelocityBrick);
            Fast2DSetVelocityBrick velocity = (Fast2DSetVelocityBrick) last;
            assertEquals("goose-test", velocity.getFormulaWithBrickField(Brick.BrickField.NAME).getFormulaTree().getValue());
        } finally {
            delete(dir);
        }
    }
}
