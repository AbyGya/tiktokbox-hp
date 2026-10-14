package com.tiktokbox.hp.chaos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Universal Edit UI: semua config (JSON) bisa diubah di sini,
 * plus export/import file. Berlaku langsung tanpa build ulang.
 */
public class ConfigEditorActivity extends Activity {
    private static final int REQ_EXPORT = 41;
    private static final int REQ_IMPORT = 42;
    private EditText jsonBox;
    private EditText serverBox;
    private CheckBox practiceBox;
    private ChaosConfig cfg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new ChaosConfig(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        sv.addView(root);
        setContentView(sv);

        root.addView(label("Server gift-listener (kosongkan = mode latihan)"));
        serverBox = new EditText(this);
        serverBox.setText(cfg.serverUrl());
        serverBox.setHint("http://192.168.1.5:8090");
        root.addView(serverBox);

        practiceBox = new CheckBox(this);
        practiceBox.setText("Mode latihan (simulasi tanpa live)");
        practiceBox.setChecked(cfg.practiceMode());
        root.addView(practiceBox);

        Button btnQuick = new Button(this);
        btnQuick.setText("Simpan cepat");
        btnQuick.setOnClickListener(v -> {
            cfg.set("__meta", "x", "x"); // pastikan section ada (no-op persist)
            try {
                org.json.JSONObject r = cfg.raw();
                r.put("serverUrl", serverBox.getText().toString().trim());
                r.put("practiceMode", practiceBox.isChecked());
                cfg.replaceAll(r.toString());
                toast("Tersimpan");
            } catch (Exception e) {
                toast("Gagal simpan");
            }
        });
        root.addView(btnQuick);

        root.addView(label("Config JSON (semua bisa diedit: giftRules, macros, jumpscares, follow)"));
        jsonBox = new EditText(this);
        jsonBox.setMinLines(14);
        jsonBox.setText(cfg.exportText());
        root.addView(jsonBox);

        Button btnSave = new Button(this);
        btnSave.setText("Simpan JSON");
        btnSave.setOnClickListener(v -> {
            if (cfg.replaceAll(jsonBox.getText().toString())) toast("Config valid & tersimpan");
            else toast("JSON tidak valid — tidak disimpan");
        });
        root.addView(btnSave);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button btnExport = new Button(this);
        btnExport.setText("Export");
        btnExport.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "chaos-config.json");
            startActivityForResult(i, REQ_EXPORT);
        });
        row.addView(btnExport);
        Button btnImport = new Button(this);
        btnImport.setText("Import");
        btnImport.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/json");
            startActivityForResult(i, REQ_IMPORT);
        });
        row.addView(btnImport);
        Button btnReset = new Button(this);
        btnReset.setText("Reset bawaan");
        btnReset.setOnClickListener(v -> {
            getSharedPreferences("chaos_config", MODE_PRIVATE).edit().clear().apply();
            cfg = new ChaosConfig(this);
            jsonBox.setText(cfg.exportText());
            serverBox.setText(cfg.serverUrl());
            practiceBox.setChecked(cfg.practiceMode());
            toast("Kembali ke bawaan");
        });
        row.addView(btnReset);
        root.addView(row);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, 16, 0, 8);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out != null) {
                    boolean ok = cfg.exportStream(out);
                    out.close();
                    toast(ok ? "Export ok" : "Export gagal");
                }
            } else if (req == REQ_IMPORT) {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in != null) {
                    boolean ok = cfg.importStream(in);
                    in.close();
                    jsonBox.setText(cfg.exportText());
                    toast(ok ? "Import ok" : "File tidak valid");
                }
            }
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }
}
