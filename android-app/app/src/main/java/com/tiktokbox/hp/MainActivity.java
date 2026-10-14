package com.tiktokbox.hp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tiktokbox.hp.chaos.CalibrationOverlay;
import com.tiktokbox.hp.chaos.ChaosConfig;
import com.tiktokbox.hp.chaos.ChaosService;
import com.tiktokbox.hp.chaos.ChaosUI;
import com.tiktokbox.hp.chaos.ConfigEditorActivity;
import com.tiktokbox.hp.chaos.MiniOverlay;
import com.tiktokbox.hp.chaos.TapAccessibilityService;

/**
 * Dashboard utama: setup 3 langkah + kendali + latihan + macro,
 * semua berbahasa Indonesia dan tombol besar. Bisa minimize ke bubble.
 */
public class MainActivity extends Activity {
    private TextView statusView;
    private TextView stepView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ChaosUI.dp(this, 14);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(ChaosUI.BG);
        sv.addView(root);
        sv.setBackgroundColor(ChaosUI.BG);
        setContentView(sv);

        // header
        LinearLayout head = ChaosUI.card(this);
        TextView title = new TextView(this);
        title.setText("\uD83D\uDCE6 TikTokBox Chaos");
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(ChaosUI.NEON);
        head.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Pengacau host FF via gift TikTok • v0.3");
        sub.setTextColor(ChaosUI.DIM);
        sub.setTextSize(13);
        head.addView(sub);
        statusView = ChaosUI.body(this, "");
        head.addView(statusView);
        root.addView(head);

        // setup 3 langkah
        LinearLayout setup = ChaosUI.card(this);
        setup.addView(ChaosUI.title(this, "🚀 Setup (3 langkah, sekali saja)"));
        stepView = ChaosUI.body(this, "");
        setup.addView(stepView);
        setup.addView(ChaosUI.row2(this,
                ChaosUI.button(this, "1️⃣ Jalankan Service", ChaosUI.GREEN),
                ChaosUI.button(this, "2️⃣ Aksesibilitas", ChaosUI.BLUE)));
        ((Button) ((LinearLayout) setup.getChildAt(2)).getChildAt(0)).setOnClickListener(v -> {
            startChaosService();
            refreshStatus();
        });
        ((Button) ((LinearLayout) setup.getChildAt(2)).getChildAt(1)).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("Aktifkan: TikTokBox Chaos");
        });
        Button b3 = ChaosUI.button(this, "3️⃣ Izin Overlay (tampil di atas game)", ChaosUI.BLUE);
        b3.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else toast("Overlay sudah aktif ✔");
            refreshStatus();
        });
        setup.addView(b3);
        setup.addView(ChaosUI.hint(this, "Urutan gampang: tekan 1️⃣, lalu 2️⃣, lalu 3️⃣ sampai semua ✔"));
        root.addView(setup);

        // kendali
        LinearLayout ctl = ChaosUI.card(this);
        ctl.addView(ChaosUI.title(this, "🎮 Kendali Live"));
        Button bMin = ChaosUI.button(this, "🔽 Minimize (bubble melayang)", ChaosUI.BLUE);
        bMin.setOnClickListener(v -> {
            MiniOverlay.start(this);
            moveTaskToBack(true);
            toast("Bubble aktif — geser ke pinggir layar");
        });
        ctl.addView(bMin);
        ctl.addView(ChaosUI.row2(this,
                panicBtn("🚨 PANIC 5 mnt", true),
                panicBtn("✅ PANIC off", false)));
        ctl.addView(ChaosUI.hint(this, "PANIC = semua efek mati total. Bubble juga punya tombol 🚨."));
        root.addView(ctl);

        // latihan
        LinearLayout lat = ChaosUI.card(this);
        lat.addView(ChaosUI.title(this, "🎁 Coba-coba (mode latihan)"));
        Button g1 = ChaosUI.button(this, "🌹 Simulasi Gift Mawar", ChaosUI.GREEN);
        g1.setOnClickListener(v -> ChaosService.practiceGift(this, "rose", "Rose", 1));
        lat.addView(g1);
        lat.addView(ChaosUI.row2(this,
                giftBtn("🦁 Gift Singa", "lion", "Lion", 29999),
                followBtn("➕ Simulasi Follow")));
        lat.addView(ChaosUI.hint(this, "Tanpa live pun bisa dicoba. Nanti gift asli otomatis jalan sendiri."));
        root.addView(lat);

        // macro FF
        LinearLayout mac = ChaosUI.card(this);
        mac.addView(ChaosUI.title(this, "🎯 Macro Emote FF"));
        Button bRec = ChaosUI.button(this, "⏺ Rekam Tap Manual", ChaosUI.AMBER);
        bRec.setOnClickListener(v -> askMacroIdAndRecord());
        mac.addView(bRec);
        Button bTry = ChaosUI.button(this, "▶ Coba Macro rose_emote", ChaosUI.GREEN);
        bTry.setOnClickListener(v -> {
            if (!TapAccessibilityService.isEnabled()) { toast("Aksesibilitas belum aktif (langkah 2️⃣)"); return; }
            ChaosConfig cfg = new ChaosConfig(this);
            TapAccessibilityService.runMacro(cfg.macros().optJSONObject("rose_emote"),
                    () -> runOnUiThread(() -> toast("Macro selesai ✔")));
        });
        mac.addView(bTry);
        mac.addView(ChaosUI.hint(this, "Rekam = buka FF, tap tombol emote 2x (buka menu, pilih emote), lalu Simpan."));
        root.addView(mac);

        // pengaturan
        LinearLayout cfg = ChaosUI.card(this);
        cfg.addView(ChaosUI.title(this, "⚙️ Pengaturan"));
        Button bCfg = ChaosUI.button(this, "✏️ Edit Semua (universal)", ChaosUI.BLUE);
        bCfg.setOnClickListener(v -> startActivity(new Intent(this, ConfigEditorActivity.class)));
        cfg.addView(bCfg);
        Button bOld = ChaosUI.button(this, "🖥 Overlay Lama (ranking)", ChaosUI.BLUE);
        bOld.setOnClickListener(v -> startActivity(new Intent(this, LegacyOverlayActivity.class)));
        cfg.addView(bOld);
        Button bStop = ChaosUI.button(this, "⏹ Matikan Service", ChaosUI.RED);
        bStop.setOnClickListener(v -> {
            MiniOverlay.stop();
            stopService(new Intent(this, ChaosService.class));
            refreshStatus();
        });
        cfg.addView(bStop);
        root.addView(cfg);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        refreshStatus();
    }

    private Button panicBtn(String t, boolean on) {
        Button x = ChaosUI.button(this, t, on ? ChaosUI.RED : ChaosUI.GREEN);
        x.setOnClickListener(v -> {
            if (on) ChaosService.panic(this, 5);
            else ChaosService.unpanic(this);
            refreshStatus();
        });
        return x;
    }

    private Button giftBtn(String t, String id, String name, long d) {
        Button x = ChaosUI.button(this, t, ChaosUI.GREEN);
        x.setOnClickListener(v -> ChaosService.practiceGift(this, id, name, d));
        return x;
    }

    private Button followBtn(String t) {
        Button x = ChaosUI.button(this, t, ChaosUI.GREEN);
        x.setOnClickListener(v -> ChaosService.practiceFollow(this, "penonton_baru"));
        return x;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void startChaosService() {
        Intent i = new Intent(this, ChaosService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void askMacroIdAndRecord() {
        if (CalibrationOverlay.isActive()) {
            CalibrationOverlay.stop(false);
            toast("Rekaman dibatalkan");
            return;
        }
        EditText et = new EditText(this);
        et.setText("rose_emote");
        new AlertDialog.Builder(this)
                .setTitle("Mau rekam macro apa?")
                .setMessage("Buka game FF dulu, tekan Mulai, lalu TAP tombol di layar sesuai urutan. Terakhir tekan Simpan di panel merah.")
                .setView(et)
                .setPositiveButton("⏺ Mulai Rekam", (d, w) -> {
                    String id = et.getText().toString().trim();
                    if (id.isEmpty()) id = "rose_emote";
                    CalibrationOverlay.start(this, id);
                    moveTaskToBack(true);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void refreshStatus() {
        ChaosConfig cfg = new ChaosConfig(this);
        boolean svc = ChaosService.isRunning();
        boolean acc = isAccessibilityOn();
        boolean ov = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        int[] st = ChaosService.stats();
        statusView.setText((svc ? "🟢" : "⚪") + " Service " + (svc ? "JALAN" : "mati")
                + "   •   🎁 " + st[1] + "   •   💎 " + st[0]
                + (cfg.panicking() ? "\n🔴 PANIC AKTIF" : ""));
        stepView.setText((svc ? "✔" : "⬜") + " 1. Service jalan\n"
                + (acc ? "✔" : "⬜") + " 2. Aksesibilitas tap\n"
                + (ov ? "✔" : "⬜") + " 3. Overlay di atas game");
    }

    private boolean isAccessibilityOn() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            for (android.accessibilityservice.AccessibilityServiceInfo info :
                    am.getEnabledAccessibilityServiceList(
                            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                        && info.getResolveInfo().serviceInfo.name.endsWith("TapAccessibilityService")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return TapAccessibilityService.isEnabled();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
