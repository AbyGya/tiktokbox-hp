package com.tiktokbox.hp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * TikTokBox HP - WebView wrapper.
 * Memuat overlay lokal (assets/overlay.html) yang bisa konek via WS ke
 * gift-listener di Termux (localhost:8090) atau PC di LAN.
 * Tanpa backend pun mini-game + tombol test gift tetap jalan lokal.
 */
public class MainActivity extends Activity {
    private WebView web;
    private static final String PREF = "tbhp";
    private static final String KEY_SRV = "server"; // cth http://192.168.1.5:8090

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());

        // inject server pilihan ke localStorage overlay sebelum load
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String srv = sp.getString(KEY_SRV, "");
        if (!srv.isEmpty()) {
            web.evaluateJavascript(null, null); // no-op, localStorage diatur via JS di overlay
        }

        web.loadUrl("file:///android_asset/overlay.html");

        // long-press = ganti server backend
        web.setOnLongClickListener(v -> { askServer(); return true; });
    }

    private void askServer() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        EditText et = new EditText(this);
        et.setText(sp.getString(KEY_SRV, "http://192.168.1.5:8090"));
        et.setHint("http://IP-PC:8090 (kosongkan = mode lokal)");
        new AlertDialog.Builder(this)
                .setTitle("Server gift-listener")
                .setMessage("Long-press menu. Isi IP backend Node (Termux/PC), atau kosongkan untuk mode lokal.")
                .setView(et)
                .setPositiveButton("Simpan & Muat", (d, w) -> {
                    String v = et.getText().toString().trim();
                    sp.edit().putString(KEY_SRV, v).apply();
                    if (!v.isEmpty()) web.loadUrl(v + "/overlay.html");
                    else web.loadUrl("file:///android_asset/overlay.html");
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
