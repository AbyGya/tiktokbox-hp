package com.tiktokbox.hp.chaos;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Eksekutor tap otomatis TANPA root via Accessibility API.
 * Macro = daftar langkah {x,y (0..1, dinormalisasi resolusi), delayMs} + jitter.
 * Dipanggil dari ChaosService / tombol "Coba" kalibrasi.
 */
public class TapAccessibilityService extends AccessibilityService {
    private static TapAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    public static class Step {
        public final float x, y; // 0..1
        public final long delayMs;
        Step(float x, float y, long d) { this.x = x; this.y = y; this.delayMs = d; }
    }

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { /* tidak butuh event */ }

    @Override
    public void onInterrupt() {}

    public static boolean isEnabled() { return instance != null; }

    public static void goHome() {
        if (instance != null) instance.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static void goBack() {
        if (instance != null) instance.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /** Jalankan macro dari JSONObject {steps:[{x,y,delayMs}], jitter}. callback via Runnable. */
    public static void runMacro(JSONObject macro, Runnable onDone) {
        if (instance == null) return;
        try {
            List<Step> steps = new ArrayList<>();
            JSONArray arr = macro.optJSONArray("steps");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    steps.add(new Step((float) s.optDouble("x"), (float) s.optDouble("y"), s.optLong("delayMs", 500)));
                }
            }
            final float jitter = (float) macro.optDouble("jitter", 0.012);
            instance.execute(steps, jitter, onDone);
        } catch (Exception ignored) {}
    }

    private void execute(List<Step> steps, float jitter, Runnable onDone) {
        if (steps.isEmpty()) {
            Toast.makeText(this, "Macro kosong — rekam dulu via Kalibrasi", Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
            return;
        }
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);
        final int W = dm.widthPixels, H = dm.heightPixels;
        runStep(steps, 0, W, H, jitter, onDone);
    }

    private void runStep(List<Step> steps, int idx, int W, int H, float jitter, Runnable onDone) {
        if (idx >= steps.size()) {
            if (onDone != null) handler.post(onDone);
            return;
        }
        Step s = steps.get(idx);
        float jx = (random.nextFloat() * 2 - 1) * jitter * W;
        float jy = (random.nextFloat() * 2 - 1) * jitter * H;
        float px = clamp(s.x * W + jx, 0, W - 1);
        float py = clamp(s.y * H + jy, 0, H - 1);
        Path p = new Path();
        p.moveTo(px, py);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 90))
                .build();
        dispatchGesture(g, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription d) {
                handler.postDelayed(() -> runStep(steps, idx + 1, W, H, jitter, onDone), Math.max(120, s.delayMs));
            }
            @Override
            public void onCancelled(GestureDescription d) {
                handler.postDelayed(() -> runStep(steps, idx + 1, W, H, jitter, onDone), Math.max(120, s.delayMs));
            }
        }, null);
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}
