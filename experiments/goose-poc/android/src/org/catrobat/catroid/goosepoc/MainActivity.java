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
            Thread wrongThread = new Thread(() -> {
                String result;
                try {
                    GooseProbe.add(1, 2);
                    result = "FAIL: call on another thread was accepted";
                } catch (IllegalStateException expected) {
                    result = "PASS: wrong-thread call rejected";
                } catch (Throwable error) {
                    result = "FAIL: " + error;
                }
                final String message = result;
                runOnUiThread(() -> text.setText("PASS: Goose JNI returned 42 and 1\n" + message));
            });
            wrongThread.start();
        } catch (Throwable error) {
            text.setText("FAIL: " + error);
        }
    }
}
