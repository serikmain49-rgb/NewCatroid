package org.catrobat.catroid.utils;

import java.util.Arrays;

/** Optional frame-interval benchmark; call from the rendering thread only. */
public final class FrameBenchmark {
    private static final long WINDOW_NS = 10_000_000_000L;
    private final long[] intervals = new long[2048];
    private long previousNs;
    private long intervalSum;
    private long sectionSum;
    private int count;

    public static final class Result {
        public final int frames;
        public final double fps;
        public final double onePercentLowFps;
        public final double measuredSectionMs;
        Result(int frames, double fps, double onePercentLowFps, double measuredSectionMs) {
            this.frames = frames; this.fps = fps; this.onePercentLowFps = onePercentLowFps;
            this.measuredSectionMs = measuredSectionMs;
        }
    }

    public Result sample(long timestampNs, boolean running, long measuredSectionNs) {
        if (!running) { reset(); return null; }
        if (previousNs == 0) { previousNs = timestampNs; return null; }
        long interval = timestampNs - previousNs;
        previousNs = timestampNs;
        if (interval <= 0) { reset(); return null; }
        intervals[count++] = interval;
        intervalSum += interval;
        sectionSum += Math.max(0, measuredSectionNs);
        if (intervalSum < WINDOW_NS && count < intervals.length) return null;
        Arrays.sort(intervals, 0, count);
        int worstCount = Math.max(1, (count + 99) / 100);
        long worstSum = 0;
        for (int i = count - worstCount; i < count; i++) worstSum += intervals[i];
        Result result = new Result(count, count * 1e9 / intervalSum,
                worstCount * 1e9 / worstSum, sectionSum / (double) count / 1e6);
        count = 0; intervalSum = 0; sectionSum = 0;
        return result;
    }

    public void reset() { previousNs = 0; intervalSum = 0; sectionSum = 0; count = 0; }
}
