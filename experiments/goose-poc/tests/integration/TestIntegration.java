import org.catrobat.catroid.fast2d.GooseMovement;
public class TestIntegration {
  public static void main(String[] args) throws Exception {
    float[] out = new float[4];
    if (!GooseMovement.update(10,-4,2,3,-5,-9.8f,.75f,.125f,out)) throw new AssertionError("native call failed");
    float vy = -5f + -9.8f * .125f;
    float[] expected = {vy,10f+3f*.125f,-4f+vy*.125f,2f+.75f*.125f};
    for (int i = 0; i < 4; i++) if (Float.floatToIntBits(out[i]) != Float.floatToIntBits(expected[i])) throw new AssertionError(i);
    Thread t = new Thread(() -> { if (GooseMovement.update(0,0,0,0,0,0,0,0,new float[4])) throw new AssertionError("cross thread accepted"); });
    final Throwable[] failure = new Throwable[1];
    t.setUncaughtExceptionHandler((thread,error) -> failure[0] = error);
    t.start(); t.join();
    if (failure[0] != null) throw new AssertionError(failure[0]);
    System.out.println("PASS: application JNI bridge and thread fallback");
  }
}
