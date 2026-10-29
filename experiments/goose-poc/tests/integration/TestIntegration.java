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
    Thread t = new Thread(() -> { if (GooseMovement.update(0,0,0,0,0,0,0,0,new float[4])) throw new AssertionError("cross thread accepted"); });
    final Throwable[] failure = new Throwable[1];
    t.setUncaughtExceptionHandler((thread,error) -> failure[0] = error);
    t.start(); t.join();
    if (failure[0] != null) throw new AssertionError(failure[0]);
    System.out.println("PASS: application JNI bridge and thread fallback");
  }
}
