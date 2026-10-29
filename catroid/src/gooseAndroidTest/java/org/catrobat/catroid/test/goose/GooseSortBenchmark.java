package org.catrobat.catroid.test.goose;

import android.util.Log;
import org.catrobat.catroid.fast2d.GooseMovement;
import static org.junit.Assert.assertEquals;

/** Worst-case reverse-depth microbenchmark, including reset and JNI copying. */
final class GooseSortBenchmark {
    private static void insertionSort(int[] ids, float[] depths) {
        for (int i = 1; i < ids.length; i++) {
            int id = ids[i], j = i;
            float depth = depths[i];
            while (j > 0 && depths[j - 1] > depth) {
                ids[j] = ids[j - 1]; depths[j] = depths[j - 1]; j--;
            }
            ids[j] = id; depths[j] = depth;
        }
    }

    static void run() {
        for (int count : new int[] {100, 1000, 5000}) {
            int rounds = count == 100 ? 1000 : count == 1000 ? 150 : 15;
            int[] inputIds = new int[count], javaIds = new int[count], gooseIds = new int[count];
            float[] inputDepth = new float[count], javaDepth = new float[count], gooseDepth = new float[count];
            for (int i = 0; i < count; i++) { inputIds[i] = i; inputDepth[i] = count - i; }
            for (int warmup = 0; warmup < 20; warmup++) {
                System.arraycopy(inputIds, 0, javaIds, 0, count);
                System.arraycopy(inputDepth, 0, javaDepth, 0, count);
                insertionSort(javaIds, javaDepth);
                System.arraycopy(inputIds, 0, gooseIds, 0, count);
                System.arraycopy(inputDepth, 0, gooseDepth, 0, count);
                if (!GooseMovement.sortDepth(gooseIds, gooseDepth, count)) throw new AssertionError("Goose sort unavailable");
            }
            long started = System.nanoTime();
            for (int i = 0; i < rounds; i++) {
                System.arraycopy(inputIds, 0, javaIds, 0, count);
                System.arraycopy(inputDepth, 0, javaDepth, 0, count);
                insertionSort(javaIds, javaDepth);
            }
            long javaNs = System.nanoTime() - started;
            started = System.nanoTime();
            for (int i = 0; i < rounds; i++) {
                System.arraycopy(inputIds, 0, gooseIds, 0, count);
                System.arraycopy(inputDepth, 0, gooseDepth, 0, count);
                if (!GooseMovement.sortDepth(gooseIds, gooseDepth, count)) throw new AssertionError("Goose sort unavailable");
            }
            long gooseNs = System.nanoTime() - started;
            for (int i = 0; i < count; i++) assertEquals("sort result " + i, javaIds[i], gooseIds[i]);
            Log.i("GOOSE_SORT_MICRO", "entities=" + count + " reverse_depth=true iterations=" + rounds
                    + " java_ns_per_sort=" + javaNs / rounds + " goose_jni_ns_per_sort=" + gooseNs / rounds);
        }
    }
}
