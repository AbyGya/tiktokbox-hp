package com.tiktokbox.hp.chaos;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tiktokbox.hp.MainActivity;

/**
 * Mode minimize: bubble melayang kecil (bisa di-drag) berisi
 * status + penghitung gift + tombol PANIC + buka penuh + tutup.
 * Cocok dipakai saat host main FF: bubble menepi di pinggir layar.
 */
public class MiniOverlay {
    private static WindowManager wm;
    private static LinearLayout root;
    private static TextView statView;
    private static Context app;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static boolean isShowing() { return root != null; }

    public static void start(Context c) {
        if (root != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) {
            Toast.makeText(c, "Aktifkan izin Overlay dulu", Toast.LENGTH_LONG).show();
            return;
        }
        app = c.getApplicationContext();
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);

        root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE141A33);
        bg.setCornerRadius(40);
        bg.setStroke(2, 0xFF00E5FF);
        root.setBackground(bg);
        int p = ChaosUI.dp(app, 8);
        root.setPadding(p, p, p, p);

        statView = new TextView(app);
        statView.setTextColor(Color.WHITE);
        statView.setTextSize(13);
        statView.setGravity(Gravity.CENTER);
        statView.setPadding(p, p / 2, p, p / 2);
        root.addView(statView);

        LinearLayout row = new LinearLayout(app);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button btnPanic = miniBtn("🚨");
        btnPanic.setOnClickListener(v -> {
            ChaosService.panic(app, 5);
            update();
            Toast.makeText(app, "PANIC 5 menit", Toast.LENGTH_SHORT).show();
        });
        Button btnOpen = miniBtn("⛶");
        btnOpen.setOnClickListener(v -> {
            Intent i = new Intent(app, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            app.startActivity(i);
        });
        Button btnClose = miniBtn("❌");
        btnClose.setOnClickListener(v -> stop());
        row.addView(btnPanic);
        row.addView(btnOpen);
        row.addView(btnClose);
        root.addView(row);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 20;
        lp.y = 300;

        // drag pakai area status
        statView.setOnTouchListener(new View.OnTouchListener() {
            int ox, oy, sx, sy;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ox = lp.x; oy = lp.y; sx = (int) e.getRawX(); sy = (int) e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = ox + (int) e.getRawX() - sx;
                        lp.y = oy + (int) e.getRawY() - sy;
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                        return true;
                }
                return false;
            }
        });

        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            root = null;
            Toast.makeText(app, "Gagal tampil mini: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        update();
    }

    private static Button miniBtn(String s) {
        Button b = new Button(app);
        b.setText(s);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF27408B);
        d.setCornerRadius(24);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ChaosUI.dp(app, 48), ChaosUI.dp(app, 48));
        lp.setMargins(ChaosUI.dp(app, 3), 0, ChaosUI.dp(app, 3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    /** Refresh tulisan status (dipanggil tiap gift / panic berubah). */
    public static void update() {
        handler.post(() -> {
            if (statView == null || app == null) return;
            int[] st = ChaosService.stats();
            boolean panic = false;
            try { panic = new ChaosConfig(app).panicking(); } catch (Exception ignored) {}
            String dot = !ChaosService.isRunning() ? "⚪" : (panic ? "🔴" : "🟢");
            statView.setText(dot + " " + st[1] + "🎁 " + st[0] + "💎");
        });
    }

    public static void stop() {
        handler.post(() -> {
            try { if (wm != null && root != null) wm.removeView(root); } catch (Exception ignored) {}
            root = null;
            statView = null;
        });
    }
}
