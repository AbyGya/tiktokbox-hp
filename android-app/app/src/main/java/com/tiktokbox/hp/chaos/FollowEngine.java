package com.tiktokbox.hp.chaos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Fitur follow unik:
 * - Sambutan Meriah: TTS sebut nama + confetti overlay.
 * - Kartu Nasib: acak Berkah (tameng anti-chaos N detik) / Kutukan (1 efek).
 * - Pasukan Rookie: tiap kelipatan N follower -> pengumuman bonus chaos.
 * Semua teks & peluang dari config (editable).
 */
public class FollowEngine {
    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private final Random random = new Random();
    private final List<String> squad = new ArrayList<>();
    private long shieldUntil = 0;

    public interface ChaosCallback {
        void onJumpscare(String tplId, String user);
        void onShield(long until);
    }

    private ChaosCallback callback;

    public FollowEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        try {
            tts = new TextToSpeech(this.ctx, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    try { tts.setLanguage(new Locale("id", "ID")); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    public void setCallback(ChaosCallback cb) { this.callback = cb; }

    public boolean shieldActive() { return System.currentTimeMillis() < shieldUntil; }

    public void onFollow(String user) {
        ChaosConfig cfg = new ChaosConfig(ctx);
        JSONObject f = cfg.follow();
        final String finalUser = (user == null || user.isEmpty()) ? "follower baru" : user;

        if (f.optBoolean("greetEnabled", true)) {
            String text = f.optString("greetTts", "Selamat datang {user}!").replace("{user}", finalUser);
            speak(text);
            confetti("\uD83C\uDF89 " + finalUser + " bergabung!");
        }
        if (f.optBoolean("cardEnabled", true)) {
            handler.postDelayed(() -> drawCard(finalUser), 2500);
        }
        // pasukan
        if (!squad.contains(finalUser)) squad.add(finalUser);
        int every = f.optInt("squadMilestoneEvery", 10);
        if (every > 0 && squad.size() % every == 0) {
            String t = f.optString("squadText", "Pasukan sudah {n}!").replace("{n}", String.valueOf(squad.size()));
            speak(t);
            confetti("\uD83C\uDF96️ " + t);
        }
    }

    private void drawCard(final String user) {
        ChaosConfig cfg = new ChaosConfig(ctx);
        JSONObject f = cfg.follow();
        double blessChance = f.optDouble("blessChance", 0.4);
        if (random.nextDouble() < blessChance) {
            int sec = f.optInt("blessShieldSec", 30);
            shieldUntil = System.currentTimeMillis() + sec * 1000L;
            String t = f.optString("blessText", "{user} dapat BERKAH!").replace("{user}", user);
            speak(t);
            confetti("✨ " + t);
            if (callback != null) callback.onShield(shieldUntil);
        } else {
            String action = f.optString("curseAction", "jumpscare");
            String param = f.optString("curseParam", "tpl_tuyul");
            speak("Yahh " + user + " dapat kutukan!");
            if ("jumpscare".equals(action) && callback != null) callback.onJumpscare(param, user);
        }
    }

    private void speak(String text) {
        try { if (tts != null) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "fol"); } catch (Exception ignored) {}
    }

    private void confetti(String text) {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextSize(22);
            tv.setTextColor(Color.WHITE);
            tv.setBackgroundColor(Color.argb(200, 20, 20, 60));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(24, 24, 24, 24);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= 26
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP;
            lp.y = 120;
            wm.addView(tv, lp);
            handler.postDelayed(() -> {
                try { wm.removeView(tv); } catch (Exception ignored) {}
            }, 3000);
        } catch (Exception ignored) {} // izin overlay belum ada -> lewati visual, TTS tetap jalan
    }

    public void shutdown() {
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
    }
}
