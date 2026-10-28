package org.catrobat.catroid.goosepoc;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setTextSize(20);
        text.setPadding(32, 48, 32, 32);
        text.setText("Running Goose JNI test...");
        setContentView(text);
        try {
            int a = GooseProbe.add(20, 22);
            int b = GooseProbe.add(-2, 3);
            if (a != 42 || b != 1) throw new AssertionError("Unexpected result: " + a + ", " + b);
            float x = 10f, y = -4f, rotation = 2f, vx = 3f, vy = -5f;
            float gravity = -9.8f, angular = 0.75f, dt = 0.125f;
            float newVy = vy + gravity * dt;
            float[] expected = { newVy, x + vx * dt, y + newVy * dt, rotation + angular * dt };
            float[] actual = GooseProbe.movement(x, y, rotation, vx, vy, gravity, angular, dt);
            if (actual == null || actual.length != expected.length) throw new AssertionError("Movement result missing");
            for (int i = 0; i < expected.length; i++) {
                if (Float.floatToIntBits(expected[i]) != Float.floatToIntBits(actual[i]))
                    throw new AssertionError("Movement mismatch at " + i + ": " + actual[i] + " != " + expected[i]);
            }
            MovementBatchTest.run();
            Thread wrongThread = new Thread(() -> {
                String result;
                try {
                    GooseProbe.add(1, 2);
                    result = "FAIL: call on another thread was accepted";
                } catch (IllegalStateException wrongThreadError) {
                    result = "PASS: wrong-thread call rejected";
                } catch (Throwable error) {
                    result = "FAIL: " + error;
                }
                final String message = result;
                runOnUiThread(() -> text.setText("PASS: Goose JNI returned 42 and 1\nPASS: Fast2D movement matches Java\nPASS: 512 objects, 120 frames\n" + message));
            });
            wrongThread.start();
        } catch (Throwable error) {
            text.setText("FAIL: " + error);
        }
    }
}
