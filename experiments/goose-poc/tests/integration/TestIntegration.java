import org.catrobat.catroid.fast2d.GooseMovement;
public class TestIntegration {
  public static void main(String[] args) throws Exception {
    float[] out = new float[4];
    if (!GooseMovement.update(10,-4,2,3,-5,-9.8f,.75f,.125f,out)) throw new AssertionError("native call failed");
    float vy = -5f + -9.8f * .125f;
    float[] expected = {vy,10f+3f*.125f,-4f+vy*.125f,2f+.75f*.125f};
    for (int i = 0; i < 4; i++) if (Float.floatToIntBits(out[i]) != Float.floatToIntBits(expected[i])) throw new AssertionError(i);
    float[] batch = {10,-4,2,3,-5,-9.8f,.75f, 7,8,9,10,11,12,13};
    if (!GooseMovement.updateBatch(batch, 1, .125f)) throw new AssertionError("batch unavailable");
    float[] first = {10f+3f*.125f,-4f+vy*.125f,2f+.75f*.125f,3f,vy,-9.8f,.75f};
    for (int i = 0; i < first.length; i++) if (Float.floatToIntBits(batch[i]) != Float.floatToIntBits(first[i])) throw new AssertionError("batch " + i);
    if (batch[7] != 7 || batch[13] != 13) throw new AssertionError("batch crossed count boundary");
    if (GooseMovement.updateBatch(batch, 3, .125f)) throw new AssertionError("invalid count accepted");
    float[] distance = new float[1];
    if (!GooseMovement.distance(1, 2, 3, 4, 6, 3, distance) || distance[0] != 5f)
        throw new AssertionError("3D distance mismatch");
    float[] rects = {0,0,1,1,99, 100,100,1,1,99, 0,0,1,1,99};
    if (!GooseMovement.visibility(rects, 2, 0, 0, 10, 10)) throw new AssertionError("visibility unavailable");
    if (rects[4] != 1f || rects[9] != 0f || rects[14] != 99f) throw new AssertionError("visibility mismatch");
    if (GooseMovement.visibility(rects, 4, 0, 0, 10, 10)) throw new AssertionError("invalid visibility count");
    java.util.Random random = new java.util.Random(87213);
    for (int i = 0; i < 20000; i++) {
      float ax = (random.nextFloat() - .5f) * 1000f, ay = (random.nextFloat() - .5f) * 1000f, az = (random.nextFloat() - .5f) * 1000f;
      float bx = (random.nextFloat() - .5f) * 1000f, by = (random.nextFloat() - .5f) * 1000f, bz = (random.nextFloat() - .5f) * 1000f;
      float dx = ax - bx, dy = ay - by, dz = az - bz;
      float expectedDistance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!GooseMovement.distance(ax, ay, az, bx, by, bz, distance) ||
          Float.floatToIntBits(expectedDistance) != Float.floatToIntBits(distance[0]))
        throw new AssertionError("3D distance mismatch " + i);
    }
    if (!GooseMovement.distance(Float.NaN,0,0,0,0,0,distance) || !Float.isNaN(distance[0]))
      throw new AssertionError("NaN distance mismatch");
    if (!GooseMovement.distance(Float.POSITIVE_INFINITY,0,0,0,0,0,distance) || !Float.isInfinite(distance[0]))
      throw new AssertionError("Infinity distance mismatch");
    float[] physics = new float[2];
    float step = 1f / 60f;
    if (!GooseMovement.physicsPlan(.1f, step, physics) || physics[0] != 3f)
      throw new AssertionError("physics plan mismatch");
    float remain = .1f; for (int j = 0; j < 3; j++) remain -= step;
    if (remain >= step) remain %= step;
    if (Float.floatToIntBits(remain) != Float.floatToIntBits(physics[1]))
      throw new AssertionError("physics remainder mismatch");
    if (GooseMovement.physicsPlan(1f, 0f, physics)) throw new AssertionError("invalid step accepted");
    int[] ids = {42, 7, 19, 900}; float[] depths = {2, -1, 2, 99};
    if (!GooseMovement.sortDepth(ids, depths, 3) || ids[0] != 7 || ids[1] != 42 || ids[2] != 19 || ids[3] != 900)
      throw new AssertionError("stable depth sort mismatch");
    if (GooseMovement.sortDepth(ids, depths, 5)) throw new AssertionError("invalid depth count");
    for (int trial = 0; trial < 300; trial++) {
      int count = trial % 61 == 0 ? 2000 : trial % 257;
      int[] actualIds = new int[count], expectedIds = new int[count];
      float[] actualZ = new float[count], expectedZ = new float[count];
      for (int i = 0; i < count; i++) {
        actualIds[i] = expectedIds[i] = i;
        float z = trial % 3 == 0 ? (float)(count - i) : random.nextInt(9) - 4;
        if (trial % 7 == 0 && i == count / 2) z = Float.NaN;
        if (trial % 13 == 0 && i == count / 3) z = Float.POSITIVE_INFINITY;
        actualZ[i] = expectedZ[i] = z;
      }
      for (int i = 1; i < count; i++) {
        int id = expectedIds[i], j = i; float z = expectedZ[i];
        while (j > 0 && expectedZ[j - 1] > z) {
          expectedIds[j] = expectedIds[j - 1]; expectedZ[j] = expectedZ[j - 1]; j--;
        }
        expectedIds[j] = id; expectedZ[j] = z;
      }
      if (!GooseMovement.sortDepth(actualIds, actualZ, count)) throw new AssertionError("sort failed");
      for (int i = 0; i < count; i++)
        if (actualIds[i] != expectedIds[i] || Float.floatToIntBits(actualZ[i]) != Float.floatToIntBits(expectedZ[i]))
          throw new AssertionError("sort mismatch " + trial + ", " + i);
    }
    for (int i = 0; i < 100000; i++) {
      float dx = (random.nextFloat() - .5f) * 1000, dy = (random.nextFloat() - .5f) * 1000;
      float cosine = random.nextFloat() * 2 - 1, sine = random.nextFloat() * 2 - 1;
      float halfWidth = random.nextFloat() * 100, halfHeight = random.nextFloat() * 100;
      boolean rotated = (i & 1) != 0;
      if (i % 997 == 0) dx = Float.NaN;
      if (i % 991 == 0) halfWidth = Float.POSITIVE_INFINITY;
      float x = rotated ? dx * cosine - dy * sine : dx;
      float y = rotated ? dx * sine + dy * cosine : dy;
      int expectedHit = x >= -halfWidth && x <= halfWidth && y >= -halfHeight && y <= halfHeight ? 1 : 0;
      if (GooseMovement.hitTest(dx, dy, cosine, sine, rotated, halfWidth, halfHeight) != expectedHit)
        throw new AssertionError("touch hit mismatch at " + i);
    }
    if (GooseMovement.hitTest(1, 0, 0, 1, true, 1, 1) != 1
        || GooseMovement.hitTest(2, 0, 0, 1, true, 1, 1) != 0)
      throw new AssertionError("rotated touch boundary mismatch");
    double[] formulaResult = {123};
    int[] code = {0, 0, 1, 0, 3, 0, 4, 5};
    double[] operands = {1.5, 2.5, 0, 3, 0, 2, 0, 0};
    if (!GooseMovement.formulaNumeric(code, operands, code.length, formulaResult) || formulaResult[0] != -6.0)
      throw new AssertionError("numeric formula mismatch");
    double[] edges = {0.0, -0.0, 1.0, -1.0, Double.MAX_VALUE, -Double.MAX_VALUE, 1e-300, Double.NaN};
    for (int i = 0; i < 40000; i++) {
      double left = i < 64 ? edges[i % 8] : random.nextGaussian() * 1e100;
      double right = i < 64 ? edges[(i / 8) % 8] : random.nextGaussian() * 1e100;
      int op = 1 + i % 4;
      double expectedFormula;
      if (op == 1) expectedFormula = left + right;
      else if (op == 2) expectedFormula = left - right;
      else if (op == 3) expectedFormula = left * right;
      else expectedFormula = right == 0.0 ? Double.NaN : left / right;
      if (expectedFormula == Double.POSITIVE_INFINITY) expectedFormula = Double.MAX_VALUE;
      if (expectedFormula == Double.NEGATIVE_INFINITY) expectedFormula = -Double.MAX_VALUE;
      if (!GooseMovement.formulaNumeric(new int[]{0, 0, op}, new double[]{left, right, 0}, 3, formulaResult)
          || Double.doubleToLongBits(expectedFormula) != Double.doubleToLongBits(formulaResult[0]))
        throw new AssertionError("numeric formula mismatch at " + i);
    }
    formulaResult[0] = -6.0;
    if (GooseMovement.formulaNumeric(new int[]{1}, new double[1], 1, formulaResult)
        || GooseMovement.formulaNumeric(new int[]{0, 99}, new double[2], 2, formulaResult)
        || GooseMovement.formulaNumeric(new int[]{0, 0}, new double[2], 2, formulaResult)
        || GooseMovement.formulaNumeric(code, operands, 257, formulaResult))
      throw new AssertionError("invalid numeric formula accepted");
    if (formulaResult[0] != -6.0) throw new AssertionError("invalid program changed result");
    Thread t = new Thread(() -> { if (GooseMovement.update(0,0,0,0,0,0,0,0,new float[4])) throw new AssertionError("cross thread accepted"); });
    final Throwable[] failure = new Throwable[1];
    t.setUncaughtExceptionHandler((thread,error) -> failure[0] = error);
    t.start(); t.join();
    if (failure[0] != null) throw new AssertionError(failure[0]);
    System.out.println("PASS: application JNI bridge and thread fallback");
  }
}
