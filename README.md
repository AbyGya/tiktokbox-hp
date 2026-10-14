# TikTokBox HP 📦

Clean-room pengganti `TikTokBox.exe` (Windows, NSIS + Virbox) buat HP Android.
Convert langsung EXE→APK tidak mungkin (x86 Win32 vs ARM + DRM), jadi bagian
"baca gift live" dibangun ulang pakai API publik.

## Isi repo

- `gift-listener/` — Node.js pembaca TikTok LIVE (chat/gift/like/follow/share/join),
  broadcast via WebSocket + REST. `1 diamond = 1 poin` (samakan TikTokBox).
- `web-overlay/` — `overlay.html`: ranking gifters + live feed + mini-game Tower
  (pengganti OnlyClimb/KarinTower) + tombol test. Bisa dipakai sebagai OBS Browser Source.
- `android-app/` — wrapper WebView → APK debug buat Android 8+ (diuji target Android 16,
  Infinix Hot 60 Pro 8GB).

## Pakai cepat (HP via Termux)

```sh
pkg install nodejs
cd gift-listener && npm install
node server.js --username <tiktok_yang_lagi_live> --port 8090
# atau tanpa live (simulasi): MOCK_MODE=true node server.js
```

Buka `http://localhost:8090/overlay.html` di Chrome HP / OBS Browser Source.
WS feed: `ws://localhost:8090/ws`. Test manual: `POST /api/test-gift`.

## APK via GitHub Actions (biar ga berat di lokal)

Push ke `main` → tab **Actions** → download artifact `tiktokbox-hp-debug` (.apk).
Install di HP: izinkan "Install unknown apps" → buka APK.
Di app: long-press untuk isi URL backend (Termux `localhost:8090` / IP PC di LAN).
Tanpa backend pun mini-game + tombol test tetap jalan lokal.

## Catatan

- Bukan decompile/redistribusi TikTokBox.exe. Jangan bypass sistem poin/bayar mereka.
- TikTok LIVE dipakai via lib publik; patuhi ToS TikTok, pakai akun test dulu.
- EXE asli hanya dianalisa statis (NSIS v3.04, payload Virbox) di sandbox, tidak dijalankan.
