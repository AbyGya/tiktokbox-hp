package com.tiktokbox.hp.chaos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay kalibrasi tap manual: lapisan transparan di atas game (mis. FF)
 * merekam posisi + jeda tiap ketukan, disimpan sebagai macro di config.
 * Cara pakai: dari MainActivity pilih macroId -> mulai -> tap tombol di game
 * -> Simpan. Tombol "Coba" mengeksekusi ulang via TapAccessibilityService.
 */
public class CalibrationOverlay {
    private static CalibrationOverlay active;

    private final Context ctx;
    private final String macroId;
    private final WindowManager wm;
    private final int screenW, screenH;
    private View recorder;
    private LinearLayout panel;
    private TextView counter;
    private final List<float[]> points = new ArrayList<>();
    private final List<Long> gaps = new ArrayList<>();
    private long lastTap = 0;

    private CalibrationOverlay(Context ctx, String macroId) {
        this.ctx = ctx.getApplicationContext();
        this.macroId = macroId;
        wm = (WindowManager) this.ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
    }

    public static boolean isActive() { return active != null; }

    public static void start(Context ctx, String macroId) {
        if (active != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) {
            Toast.makeText(ctx, "Aktifkan izin Overlay dulu", Toast.LENGTH_LONG).show();
            return;
        }
        active = new CalibrationOverlay(ctx, macroId);
        active.show();
        Toast.makeText(ctx, "Mode rekam: tap tombol di game, lalu Simpan", Toast.LENGTH_LONG).show();
    }

    public static void stop(boolean save) {
        if (active != null) {
            if (save) active.save();
            active.hide();
            active = null;
        }
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private void show() {
        recorder = new View(ctx);
        recorder.setBackgroundColor(Color.argb(30, 255, 0, 0)); // merah samar biar ketahuan mode rekam
        recorder.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float nx = e.getRawX() / screenW;
                float ny = e.getRawY() / screenH;
                long now = System.currentTimeMillis();
                long gap = lastTap == 0 ? 600 : Math.min(5000, now - lastTap);
                lastTap = now;
                points.add(new float[]{nx, ny});
                gaps.add(gap);
                updateCounter();
            }
            return true; // telan sentuhan selama merekam
        });
        WindowManager.LayoutParams lpR = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        try { wm.addView(recorder, lpR); } catch (Exception e) { active = null; return; }

        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(220, 10, 10, 30));
        panel.setPadding(16, 16, 16, 16);
        counter = new TextView(ctx);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(14);
        panel.addView(counter);
        updateCounter();

        Button btnSave = new Button(ctx);
        btnSave.setText("Simpan (" + macroId + ")");
        btnSave.setOnClickListener(v -> stop(true));
        panel.addView(btnSave);

        Button btnCancel = new Button(ctx);
        btnCancel.setText("Batal");
        btnCancel.setOnClickListener(v -> stop(false));
        panel.addView(btnCancel);

        WindowManager.LayoutParams lpP = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lpP.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lpP.y = 80;
        try { wm.addView(panel, lpP); } catch (Exception ignored) {}
    }

    private void updateCounter() {
        if (counter != null) counter.setText("Macro: " + macroId + " | tap: " + points.size());
    }

    private void save() {
        try {
            ChaosConfig cfg = new ChaosConfig(ctx);
            JSONObject macros = cfg.macros();
            JSONObject m = macros.optJSONObject(macroId);
            if (m == null) {
                m = new JSONObject();
                m.put("label", macroId);
                m.put("jitter", 0.012);
            }
            JSONArray steps = new JSONArray();
            for (int i = 0; i < points.size(); i++) {
                JSONObject s = new JSONObject();
                s.put("x", points.get(i)[0]);
                s.put("y", points.get(i)[1]);
                s.put("delayMs", gaps.get(i));
                steps.put(s);
            }
            m.put("steps", steps);
            macros.put(macroId, m);
            cfg.set("macros", macroId, m); // persist via section put
            Toast.makeText(ctx, "Macro " + macroId + " tersimpan (" + points.size() + " tap)", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "Gagal simpan macro", Toast.LENGTH_LONG).show();
        }
    }

    private void hide() {
        try { if (recorder != null) wm.removeView(recorder); } catch (Exception ignored) {}
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {}
        recorder = null;
        panel = null;
    }
}
