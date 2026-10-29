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
    Thread t = new Thread(() -> { if (GooseMovement.update(0,0,0,0,0,0,0,0,new float[4])) throw new AssertionError("cross thread accepted"); });
    final Throwable[] failure = new Throwable[1];
    t.setUncaughtExceptionHandler((thread,error) -> failure[0] = error);
    t.start(); t.join();
    if (failure[0] != null) throw new AssertionError(failure[0]);
    System.out.println("PASS: application JNI bridge and thread fallback");
  }
}
