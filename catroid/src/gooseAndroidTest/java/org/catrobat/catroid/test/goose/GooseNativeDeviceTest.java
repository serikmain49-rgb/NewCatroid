package org.catrobat.catroid.test.goose;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.catrobat.catroid.fast2d.GooseMovement;
import org.catrobat.catroid.formulaeditor.FormulaElement;
import org.catrobat.catroid.formulaeditor.FormulaElement.ElementType;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the production JNI bridge on Android, not just the host JVM. */
@RunWith(AndroidJUnit4.class)
public class GooseNativeDeviceTest {
    @Test public void movementVisibilityDistanceAndPhysics() {
        float[] movement = {10f, -4f, 2f, 3f, -5f, -9.8f, .75f};
        float dt = .125f;
        float vy = movement[4] + movement[5] * dt;
        assertTrue("Goose batch unavailable", GooseMovement.updateBatch(movement, 1, dt));
        assertEquals(10f + 3f * dt, movement[0], 0f);
        assertEquals(-4f + vy * dt, movement[1], 0f);
        assertEquals(2f + .75f * dt, movement[2], 0f);
        assertEquals(vy, movement[4], 0f);

        float[] rectangles = {0, 0, 1, 1, -1, 100, 100, 1, 1, -1};
        assertTrue(GooseMovement.visibility(rectangles, 2, 0, 0, 10, 10));
        assertEquals(1f, rectangles[4], 0f);
        assertEquals(0f, rectangles[9], 0f);

        int[] ids = {42, 7, 19}; float[] depths = {2, -1, 2};
        assertTrue(GooseMovement.sortDepth(ids, depths, 3));
        assertEquals(7, ids[0]); assertEquals(42, ids[1]); assertEquals(19, ids[2]);

        float[] distance = new float[1];
        assertTrue(GooseMovement.distance(1, 2, 3, 4, 6, 3, distance));
        assertEquals(5f, distance[0], 0f);

        // This runs on the same test thread as the native calls above.
        assertTrue("Formula prototype flag must be enabled", org.catrobat.catroid.BuildConfig.FEATURE_GOOSE_FORMULA);
        double[] formulaResult = new double[1];
        assertTrue(GooseMovement.formulaNumeric(new int[] {0, 0, 1},
                new double[] {2, 3, 0}, 3, formulaResult));
        assertEquals(5d, formulaResult[0], 0d);
        FormulaElement left = new FormulaElement(ElementType.NUMBER, "4", null);
        FormulaElement right = new FormulaElement(ElementType.NUMBER, "2", null);
        FormulaElement expression = new FormulaElement(ElementType.OPERATOR, "DIVIDE", null, left, right);
        assertEquals(2d, (Double) expression.interpretRecursive(null), 0d);
        FormulaElement zero = new FormulaElement(ElementType.NUMBER, "0", null);
        expression = new FormulaElement(ElementType.OPERATOR, "DIVIDE", null, left, zero);
        assertTrue(Double.isNaN((Double) expression.interpretRecursive(null)));
        FormulaElement max = new FormulaElement(ElementType.NUMBER, Double.toString(Double.MAX_VALUE), null);
        expression = new FormulaElement(ElementType.OPERATOR, "PLUS", null, max, max);
        assertEquals(Double.MAX_VALUE, (Double) expression.interpretRecursive(null), 0d);
        FormulaElement text = new FormulaElement(ElementType.STRING, "not a number", null);
        expression = new FormulaElement(ElementType.OPERATOR, "PLUS", null, left, text);
        assertTrue(Double.isNaN((Double) expression.interpretRecursive(null)));

        float[] plan = new float[2];
        float step = 1f / 60f;
        assertTrue(GooseMovement.physicsPlan(.1f, step, plan));
        assertEquals(3f, plan[0], 0f);
        float remainder = .1f;
        for (int i = 0; i < 3; i++) remainder -= step;
        if (remainder >= step) remainder %= step;
        assertEquals(remainder, plan[1], 0f);
    }
}
