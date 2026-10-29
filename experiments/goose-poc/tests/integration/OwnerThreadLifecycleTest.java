import java.util.concurrent.atomic.AtomicReference;
import org.catrobat.catroid.fast2d.GooseMovement;

public final class OwnerThreadLifecycleTest {
    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            try {
                if (!GooseMovement.update(0,0,0,0,0,0,0,0,new float[4]))
                    throw new AssertionError("initializing thread did not get Goose");
            } catch (Throwable error) { failure.set(error); }
        });
        owner.start(); owner.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        for (int i = 0; i < 200; i++) {
            Thread replacement = new Thread(() -> {
                try {
                    if (GooseMovement.update(0,0,0,0,0,0,0,0,new float[4]))
                        throw new AssertionError("new thread reused Goose owner");
                } catch (Throwable error) { failure.set(error); }
            });
            replacement.start(); replacement.join();
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
        System.out.println("PASS: no Goose owner reuse after original thread exits");
    }
}
