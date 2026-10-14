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
import com.tiktokbox.hp.chaos.ConfigEditorActivity;
import com.tiktokbox.hp.chaos.TapAccessibilityService;

/**
 * Menu utama TikTokBox Chaos: status izin, service, kalibrasi macro,
 * latihan gift/follow, PANIC, dan editor config universal.
 */
public class MainActivity extends Activity {
    private TextView statusView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("\uD83D\uDCE6 TikTokBox Chaos v0.2");
        title.setTextSize(22);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setPadding(0, 12, 0, 12);
        root.addView(statusView);

        addBtn(root, "▶ Jalankan Chaos Service", v -> {
            startForegroundServiceCompat();
            refreshStatus();
        });
        addBtn(root, "⏹ Stop Service", v -> {
            stopService(new Intent(this, ChaosService.class));
            refreshStatus();
        });
        addBtn(root, "♿ Aktifkan Aksesibilitas Tap", v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("Aktifkan: TikTokBox Chaos");
        });
        addBtn(root, "🪟 Izin Overlay", v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else toast("Overlay sudah aktif");
            refreshStatus();
        });
        addBtn(root, "🎯 Rekam Macro (tap manual di layar)", v -> askMacroIdAndRecord());
        addBtn(root, "▶ Coba Macro rose_emote", v -> {
            if (!TapAccessibilityService.isEnabled()) { toast("Aksesibilitas belum aktif"); return; }
            ChaosConfig cfg = new ChaosConfig(this);
            TapAccessibilityService.runMacro(cfg.macros().optJSONObject("rose_emote"),
                    () -> runOnUiThread(() -> toast("Macro selesai")));
        });
        addBtn(root, "🌹 Latihan: Gift Rose", v -> {
            ChaosService.practiceGift(this, "rose", "Rose", 1);
        });
        addBtn(root, "🦁 Latihan: Gift Lion", v -> {
            ChaosService.practiceGift(this, "lion", "Lion", 29999);
        });
        addBtn(root, "➕ Latihan: Follow", v -> {
            ChaosService.practiceFollow(this, "penonton_baru");
        });
        addBtn(root, "🚨 PANIC 5 menit", v -> {
            ChaosService.panic(this, 5);
            refreshStatus();
        });
        addBtn(root, "✅ PANIC off", v -> {
            ChaosService.unpanic(this);
            refreshStatus();
        });
        addBtn(root, "⚙ Edit Config Universal", v -> {
            startActivity(new Intent(this, ConfigEditorActivity.class));
        });
        addBtn(root, "🖥 Overlay Lama", v -> {
            startActivity(new Intent(this, LegacyOverlayActivity.class));
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void startForegroundServiceCompat() {
        Intent i = new Intent(this, ChaosService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void addBtn(LinearLayout root, String text, android.view.View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setOnClickListener(l);
        root.addView(btn);
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
                .setTitle("ID Macro")
                .setMessage("Buka FF dulu, lalu tap tombol sesuai urutan (mis. buka emote, pilih emote).")
                .setView(et)
                .setPositiveButton("Mulai Rekam", (d, w) -> {
                    String id = et.getText().toString().trim();
                    if (id.isEmpty()) id = "rose_emote";
                    CalibrationOverlay.start(this, id);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void refreshStatus() {
        ChaosConfig cfg = new ChaosConfig(this);
        boolean acc = isAccessibilityOn();
        boolean ov = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        String s = "Service: " + (ChaosService.isRunning() ? "JALAN" : "mati")
                + "\nAksesibilitas tap: " + (acc ? "AKTIF" : "MATI")
                + "\nOverlay: " + (ov ? "AKTIF" : "MATI")
                + "\nMode: " + (cfg.practiceMode() ? "latihan" : "live (" + cfg.serverUrl() + ")")
                + "\nPANIC: " + (cfg.panicking() ? "AKTIF" : "off");
        statusView.setText(s);
    }

    private boolean isAccessibilityOn() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            String target = getPackageName() + "/" + TapAccessibilityService.class.getName();
            for (android.accessibilityservice.AccessibilityServiceInfo info :
                    am.getEnabledAccessibilityServiceList(
                            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (target.equals(info.getResolveInfo().serviceInfo.name)
                        || (getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                        && TapAccessibilityService.class.getSimpleName().equals(
                                info.getResolveInfo().serviceInfo.name.substring(
                                        info.getResolveInfo().serviceInfo.name.lastIndexOf('.') + 1)))) {
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
