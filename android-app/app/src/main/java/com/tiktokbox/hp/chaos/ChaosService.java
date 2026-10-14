package com.tiktokbox.hp.chaos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.Locale;

/**
 * Service foreground: konek WS ke gift-listener, eksekusi aksi chaos.
 * Mendukung mode latihan (tanpa live) + PANIC + tameng berkah follower.
 */
public class ChaosService extends Service {
    private static final String CH = "chaos";
    private static ChaosService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebSocketClient ws;
    private JumpscareEngine jumpscare;
    private FollowEngine follow;
    private TextToSpeech tts;
    private long lastAction = 0;
    private int reconnectDelay = 3000;
    private int totalDiamonds = 0;
    private int totalGiftCount = 0;

    public static boolean isRunning() { return instance != null; }

    /** {diamonds, giftCount} sejak service jalan (untuk bubble mini). */
    public static int[] stats() {
        if (instance == null) return new int[]{0, 0};
        return new int[]{instance.totalDiamonds, instance.totalGiftCount};
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startFg();
        jumpscare = new JumpscareEngine(this);
        follow = new FollowEngine(this);
        follow.setCallback(new FollowEngine.ChaosCallback() {
            @Override public void onJumpscare(String tplId, String user) { fireJumpscare(tplId, user); }
            @Override public void onShield(long until) { toast("Tameng aktif!"); }
        });
        try {
            tts = new TextToSpeech(this, s -> {
                if (s == TextToSpeech.SUCCESS && tts != null) {
                    try { tts.setLanguage(new Locale("id", "ID")); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
        connect();
    }

    private void startFg() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                nm.createNotificationChannel(new NotificationChannel(CH, "TikTokBox Chaos", NotificationManager.IMPORTANCE_LOW));
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CH)
                    : new Notification.Builder(this);
            b.setContentTitle("TikTokBox Chaos aktif")
                    .setContentText("Menunggu gift / mode latihan")
                    .setSmallIcon(android.R.drawable.ic_dialog_info);
            Notification n = b.build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, n);
            }
        } catch (Exception e) {
            toast("Gagal start foreground: " + e.getMessage());
        }
    }

    // ---------------- WebSocket ----------------
    private void connect() {
        closeWs();
        final String base;
        try { base = new ChaosConfig(this).serverUrl(); } catch (Exception e) { return; }
        if (base == null || base.isEmpty()) return; // mode latihan / belum diset
        try {
            String url = base.replace("http", "ws") + "/ws";
            ws = new WebSocketClient(new URI(url)) {
                @Override public void onOpen(ServerHandshake h) {
                    reconnectDelay = 3000;
                    handler.post(() -> toast("Terkonek: " + base));
                }
                @Override public void onMessage(String message) { handleServerMessage(message); }
                @Override public void onClose(int code, String reason, boolean remote) { scheduleReconnect(); }
                @Override public void onError(Exception ex) { scheduleReconnect(); }
            };
            ws.connect();
        } catch (Exception ignored) {}
    }

    private void scheduleReconnect() {
        handler.removeCallbacks(reconnectTask);
        handler.postDelayed(reconnectTask, reconnectDelay);
        reconnectDelay = Math.min(60000, reconnectDelay * 2);
    }

    private final Runnable reconnectTask = this::connect;

    private void closeWs() {
        try { if (ws != null) ws.close(); } catch (Exception ignored) {}
        ws = null;
    }

    private void handleServerMessage(String message) {
        try {
            JSONObject p = new JSONObject(message);
            if (!p.has("event")) return;
            JSONObject e = p.getJSONObject("event");
            String kind = e.optString("kind", "");
            if ("gift".equals(kind)) {
                onGift(e.optString("user", ""), e.optString("giftId", ""),
                        e.optString("giftName", ""), e.optLong("diamonds", 0),
                        e.optInt("count", 1));
            } else if ("follow".equals(kind)) {
                onFollow(e.optString("user", ""));
            }
        } catch (Exception ignored) {}
    }

    // ---------------- dispatch ----------------
    private boolean gate() {
        ChaosConfig cfg = new ChaosConfig(this);
        if (cfg.panicking()) return false;
        long now = System.currentTimeMillis();
        if (now - lastAction < cfg.globalCooldownMs()) return false;
        lastAction = now;
        return true;
    }

    public void onGift(String user, String giftId, String giftName, long diamonds, int count) {
        totalGiftCount += Math.max(1, count);
        totalDiamonds += (int) Math.min(Integer.MAX_VALUE - totalDiamonds, diamonds * Math.max(1, count));
        MiniOverlay.update();
        if (!gate()) return;
        if (follow.shieldActive()) { toast("Tameng menahan gift " + giftName); return; }
        ChaosConfig cfg = new ChaosConfig(this);
        String[] ap = cfg.actionForGift(giftId, giftName, diamonds * Math.max(1, count));
        runAction(ap[0], ap[1], user);
    }

    public void onFollow(String user) {
        follow.onFollow(user == null ? "" : user);
    }

    private void runAction(String action, String param, String user) {
        if (action == null || action.isEmpty()) return;
        switch (action) {
            case "macro":
                fireMacro(param, user);
                break;
            case "jumpscare":
                fireJumpscare(param, user);
                break;
            case "tts":
                speak(param.replace("{user}", user == null ? "" : user));
                break;
            case "home":
                TapAccessibilityService.goHome();
                break;
            case "back":
                TapAccessibilityService.goBack();
                break;
            default:
                break;
        }
    }

    private void fireMacro(String macroId, String user) {
        if (!TapAccessibilityService.isEnabled()) {
            toast("Aktifkan Aksesibilitas Tap dulu");
            return;
        }
        ChaosConfig cfg = new ChaosConfig(this);
        JSONObject m = cfg.macros().optJSONObject(macroId);
        if (m == null) { toast("Macro " + macroId + " belum ada"); return; }
        toast("Gift dari " + user + " -> macro " + macroId);
        TapAccessibilityService.runMacro(m, null);
    }

    private void fireJumpscare(String tplId, String user) {
        if (!jumpscare.fire(tplId, user)) {
            // kemungkinan cooldown / belum ada izin overlay: diam saja
        }
    }

    private void speak(String text) {
        try { if (tts != null) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "ch"); } catch (Exception ignored) {}
    }

    public void quickVibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.min(ms, 5000), 255));
            else v.vibrate(Math.min(ms, 5000));
        } catch (Exception ignored) {}
    }

    private void toast(String s) {
        handler.post(() -> {
            try { Toast.makeText(ChaosService.this, s, Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
        });
    }

    // ---------------- API statis untuk MainActivity (mode latihan) ----------------
    public static void practiceGift(Context c, String giftId, String giftName, long diamonds) {
        if (instance != null) instance.onGift("kamu_latihan", giftId, giftName, diamonds, 1);
    }

    public static void practiceFollow(Context c, String user) {
        if (instance != null) instance.onFollow(user);
    }

    public static void panic(Context c, int minutes) {
        new ChaosConfig(c).setPanicMinutes(minutes);
        if (instance != null) {
            instance.jumpscare.dismiss();
            instance.toast("PANIC: efek mati " + minutes + " menit");
        }
        MiniOverlay.update();
    }

    public static void unpanic(Context c) {
        new ChaosConfig(c).clearPanic();
        if (instance != null) instance.toast("PANIC off");
        MiniOverlay.update();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(reconnectTask);
        closeWs();
        if (jumpscare != null) jumpscare.shutdown();
        if (follow != null) follow.shutdown();
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
