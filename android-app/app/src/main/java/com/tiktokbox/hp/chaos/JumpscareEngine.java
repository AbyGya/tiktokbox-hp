package com.tiktokbox.hp.chaos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Jumpscare fullscreen: gambar custom (uri galeri) atau emoji+teks bawaan,
 * suara custom (uri) atau TTS, getar, durasi — SEMUA dari config (editable).
 */
public class JumpscareEngine {
    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private final Map<String, Long> lastFired = new HashMap<>();
    private View current;
    private MediaPlayer player;

    public JumpscareEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        try {
            tts = new TextToSpeech(this.ctx, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    try { tts.setLanguage(new Locale("id", "ID")); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    /** @return false jika masih cooldown / config tidak ada */
    public boolean fire(String tplId, String user) {
        ChaosConfig cfg = new ChaosConfig(ctx);
        JSONObject tpl = cfg.jumpscares().optJSONObject(tplId);
        if (tpl == null) return false;
        long now = System.currentTimeMillis();
        long cd = tpl.optLong("cooldownMs", 20000);
        Long last = lastFired.get(tplId);
        if (last != null && now - last < cd) return false;
        lastFired.put(tplId, now);

        dismiss();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return false;

        FrameLayout root = new FrameLayout(ctx);
        try {
            root.setBackgroundColor(Color.parseColor(tpl.optString("bg", "#000000")));
        } catch (Exception e) {
            root.setBackgroundColor(Color.BLACK);
        }
        String imgUri = tpl.optString("imageUri", "");
        if (!imgUri.isEmpty()) {
            try {
                ImageView iv = new ImageView(ctx);
                iv.setImageURI(Uri.parse(imgUri));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                root.addView(iv, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            } catch (Exception ignored) {}
        }
        TextView tv = new TextView(ctx);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(34);
        String emoji = tpl.optString("emoji", "\uD83D\uDC7B");
        String title = tpl.optString("title", "JUMPSCARE");
        tv.setText((imgUri.isEmpty() ? emoji + "\n" : "") + title
                + (user != null && !user.isEmpty() ? "\noleh " + user : ""));
        root.addView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            return false; // izin overlay belum diberikan
        }
        current = root;

        // suara: custom uri > TTS
        String sndUri = tpl.optString("soundUri", "");
        String ttsText = tpl.optString("tts", "").replace("{user}", user == null ? "" : user);
        if (!sndUri.isEmpty()) {
            try {
                stopPlayer();
                player = new MediaPlayer();
                player.setDataSource(ctx, Uri.parse(sndUri));
                player.prepare();
                player.start();
            } catch (Exception ignored) {}
        } else if (!ttsText.isEmpty() && tts != null) {
            try { tts.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, "js"); } catch (Exception ignored) {}
        }
        // getar
        long vib = tpl.optLong("vibrateMs", 1000);
        if (vib > 0) {
            try {
                Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.min(vib, 5000), 255));
                    else v.vibrate(Math.min(vib, 5000));
                }
            } catch (Exception ignored) {}
        }
        handler.postDelayed(this::dismiss, Math.max(1000, tpl.optInt("durationMs", 4000)));
        return true;
    }

    public void dismiss() {
        if (current != null) {
            try {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) wm.removeView(current);
            } catch (Exception ignored) {}
            current = null;
        }
        stopPlayer();
        try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
    }

    private void stopPlayer() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
            }
        } catch (Exception ignored) {}
        player = null;
    }

    public void shutdown() {
        dismiss();
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
    }
}
