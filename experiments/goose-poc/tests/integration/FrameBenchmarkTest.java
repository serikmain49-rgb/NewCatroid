import org.catrobat.catroid.utils.FrameBenchmark;

public final class FrameBenchmarkTest {
    public static void main(String[] args) {
        FrameBenchmark benchmark = new FrameBenchmark();
        long timestamp = 1;
        if (benchmark.sample(timestamp, true, 0) != null) throw new AssertionError();
        FrameBenchmark.Result result = null;
        for (int i = 0; i < 1000; i++) {
            timestamp += 10_000_000L;
            result = benchmark.sample(timestamp, true, 2_000_000L);
            if (result != null) break;
        }
        if (result == null || result.frames != 1000) throw new AssertionError("unexpected window");
        if (Math.abs(result.fps - 100) > .01 || Math.abs(result.onePercentLowFps - 100) > .01)
            throw new AssertionError("unexpected constant frame rate");
        if (Math.abs(result.measuredSectionMs - 2) > .001) throw new AssertionError("section time");
        benchmark.sample(timestamp + 10_000_000L, false, 0);
        if (benchmark.sample(timestamp + 10_000_000_000L, true, 0) != null) throw new AssertionError("pause counted");
        FrameBenchmark low = new FrameBenchmark();
        long now = 1; low.sample(now, true, 0);
        FrameBenchmark.Result last = null;
        for (int i=0; i<901; i++) {
            now += i == 900 ? 1_000_000_000L : 10_000_000L;
            last = low.sample(now, true, 0);
        }
        if (last == null || last.onePercentLowFps >= last.fps) throw new AssertionError("low FPS missing");
        System.out.println("PASS: frame benchmark, worst frames and pause handling");
    }
}
