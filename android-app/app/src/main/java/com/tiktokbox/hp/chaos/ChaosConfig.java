package com.tiktokbox.hp.chaos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Prinsip Universal Edit: SELURUH perilaku chaos-engine berasal dari JSON ini.
 * Semua bisa diubah di app (ConfigEditorActivity) atau via export/import file.
 * Tidak ada nilai hardcode yang butuh build ulang.
 */
public class ChaosConfig {
    private static final String PREF = "chaos_config";
    private static final String KEY_JSON = "json_v1";

    private final SharedPreferences sp;
    private JSONObject root;

    public ChaosConfig(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_JSON, null);
        if (raw == null) {
            root = defaults();
            persist();
        } else {
            try {
                root = new JSONObject(raw);
            } catch (Exception e) {
                root = defaults();
            }
        }
    }

    public JSONObject raw() { return root; }

    /** Ganti seluruh config dari teks JSON (validasi dulu). */
    public boolean replaceAll(String jsonText) {
        try {
            JSONObject o = new JSONObject(jsonText);
            o.optJSONObject("follow"); // sentuh agar pasti objek
            root = o;
            ensureSections();
            persist();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void set(String section, String key, Object val) {
        try {
            ensureSections();
            root.getJSONObject(section).put(key, val);
            persist();
        } catch (Exception ignored) {}
    }

    public String exportText() {
        try { return root.toString(2); } catch (Exception e) { return root.toString(); }
    }

    public boolean importStream(InputStream in) {
        try {
            byte[] buf = new byte[65536];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            return replaceAll(sb.toString());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean exportStream(OutputStream out) {
        try {
            out.write(exportText().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- akses cepat yang sering dipakai engine ----
    public String serverUrl() { return root.optString("serverUrl", ""); }
    public boolean practiceMode() { return root.optBoolean("practiceMode", true); }
    public long panicUntil() { return root.optLong("panicUntil", 0); }
    public boolean panicking() { return System.currentTimeMillis() < panicUntil(); }
    public void setPanicMinutes(int m) {
        try { root.put("panicUntil", System.currentTimeMillis() + m * 60000L); persist(); }
        catch (Exception ignored) {}
    }
    public void clearPanic() {
        try { root.put("panicUntil", 0); persist(); } catch (Exception ignored) {}
    }
    public JSONObject macros() { return root.optJSONObject("macros"); }
    public JSONObject jumpscares() { return root.optJSONObject("jumpscares"); }
    public JSONArray giftRules() { return root.optJSONArray("giftRules"); }
    public JSONObject follow() { return root.optJSONObject("follow"); }
    public long globalCooldownMs() { return root.optLong("globalCooldownMs", 2000); }

    /** Cari aksi untuk gift: cocokkan giftId/nama dulu, lalu tier diamond tertinggi yang memenuhi. */
    public String[] actionForGift(String giftId, String giftName, long diamonds) {
        try {
            JSONArray rules = giftRules();
            String id = giftId == null ? "" : giftId.toLowerCase();
            String nm = giftName == null ? "" : giftName.toLowerCase();
            // 1) match spesifik
            for (int i = 0; i < rules.length(); i++) {
                JSONObject r = rules.getJSONObject(i);
                String match = r.optString("match", "").toLowerCase();
                if (!match.isEmpty() && !match.equals("*") && (match.equals(id) || match.equals(nm))) {
                    if (diamonds >= r.optLong("minDiamonds", 0)) return new String[]{r.optString("action", ""), r.optString("param", "")};
                }
            }
            // 2) tier wildcard: minDiamonds terbesar yang <= diamonds
            String bestA = "", bestP = "";
            long bestMin = -1;
            for (int i = 0; i < rules.length(); i++) {
                JSONObject r = rules.getJSONObject(i);
                if (!r.optString("match", "").equals("*")) continue;
                long m = r.optLong("minDiamonds", 0);
                if (diamonds >= m && m > bestMin) { bestMin = m; bestA = r.optString("action", ""); bestP = r.optString("param", ""); }
            }
            if (bestMin >= 0) return new String[]{bestA, bestP};
        } catch (Exception ignored) {}
        return new String[]{"", ""};
    }

    private void persist() { sp.edit().putString(KEY_JSON, root.toString()).apply(); }

    private void ensureSections() {
        try {
            if (root.optJSONObject("macros") == null) root.put("macros", new JSONObject());
            if (root.optJSONObject("jumpscares") == null) root.put("jumpscares", defaults().getJSONObject("jumpscares"));
            if (root.optJSONArray("giftRules") == null) root.put("giftRules", defaults().getJSONArray("giftRules"));
            if (root.optJSONObject("follow") == null) root.put("follow", defaults().getJSONObject("follow"));
        } catch (Exception ignored) {}
    }

    // ---------------- bawaan ----------------
    private static JSONObject defaults() {
        try {
            JSONObject r = new JSONObject();
            r.put("serverUrl", "");
            r.put("practiceMode", true);
            r.put("panicUntil", 0);
            r.put("globalCooldownMs", 2000);

            JSONObject macros = new JSONObject();
            JSONObject emote = new JSONObject();
            emote.put("label", "Rose -> Emote FF (isi via Rekam Macro)");
            emote.put("jitter", 0.012);
            emote.put("steps", new JSONArray()); // diisi dari kalibrasi: [{x,y,delayMs}]
            macros.put("rose_emote", emote);
            r.put("macros", macros);

            JSONObject js = new JSONObject();
            js.put("tpl_pakde", tpl("PAKDE DATANG", "\uD83E\uDDD1\u200D\uD83D\uDCBC",
                    "Waduh! Pakde datang! Awas kena razia build!",
                    "#101020", 4000, 1500, 30000));
            js.put("tpl_tuyul", tpl("TUYUL NGAKAK", "\uD83D\uDC7B",
                    "Tuyul ngakak! Host panik!",
                    "#200505", 3500, 1200, 20000));
            js.put("tpl_hajatan", tpl("ALARM HAJATAN", "\uD83D\uDCE2",
                    "Hajatan dimulai! Dangdutan dulu!",
                    "#203010", 3500, 1000, 20000));
            js.put("tpl_hantu", tpl("HANTU MINIMAP", "\uD83D\uDC79",
                    "Hantu menutup minimap! Lihat kanan kiri!",
                    "#000000", 5000, 800, 25000));
            js.put("tpl_nuke", tpl("NUKE SULTAN", "☢️",
                    "Nuke sultan! Semua kena!",
                    "#300000", 8000, 3000, 60000));
            r.put("jumpscares", js);

            JSONArray rules = new JSONArray();
            rules.put(rule("rose", 0, "macro", "rose_emote"));
            rules.put(rule("tiktok", 0, "tts", "Terima kasih {user}!"));
            rules.put(rule("*", 5, "jumpscare", "tpl_tuyul"));
            rules.put(rule("*", 10, "jumpscare", "tpl_hajatan"));
            rules.put(rule("*", 50, "jumpscare", "tpl_hantu"));
            rules.put(rule("*", 1000, "jumpscare", "tpl_nuke"));
            r.put("giftRules", rules);

            JSONObject f = new JSONObject();
            f.put("greetTts", "Selamat datang {user}! Silakan duduk, host lagi keringatan!");
            f.put("greetEnabled", true);
            f.put("cardEnabled", true);
            f.put("blessChance", 0.4);
            f.put("blessText", "{user} dapat BERKAH! Host dapat tameng 30 detik!");
            f.put("blessShieldSec", 30);
            f.put("curseAction", "jumpscare");
            f.put("curseParam", "tpl_tuyul");
            f.put("squadMilestoneEvery", 10);
            f.put("squadText", "Pasukan sudah {n} orang! Bonus chaos!");
            r.put("follow", f);
            return r;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject tpl(String title, String emoji, String tts, String bg,
                                  int dur, int vib, int cd) throws Exception {
        JSONObject o = new JSONObject();
        o.put("title", title);
        o.put("emoji", emoji);
        o.put("tts", tts);
        o.put("bg", bg);
        o.put("durationMs", dur);
        o.put("vibrateMs", vib);
        o.put("cooldownMs", cd);
        o.put("imageUri", ""); // custom: uri galeri, kosongkan = pakai emoji+teks
        o.put("soundUri", ""); // custom: uri suara, kosongkan = TTS + nada bawaan
        return o;
    }

    private static JSONObject rule(String match, long minD, String action, String param) throws Exception {
        JSONObject o = new JSONObject();
        o.put("match", match);
        o.put("minDiamonds", minD);
        o.put("action", action);
        o.put("param", param);
        return o;
    }
}
