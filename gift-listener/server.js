#!/usr/bin/env node
/**
 * TikTokBox HP - Gift Listener (clean-room, bukan decompile TikTokBox.exe)
 * Hasil bongkar: TikTokBox.exe = NSIS v3.04 installer + payload terproteksi Virbox.
 * Convert langsung EXE->APK tidak mungkin (x86 Win32 vs ARM Android + DRM).
 * Jadi ini implementasi ulang bagian "baca gift live" pakai API publik.
 *
 * Cara pakai:
 *   npm install
 *   node server.js --username <tiktok_username_live> [--port 8090]
 *   MOCK_MODE=true node server.js   (simulasi gift tanpa live, buat test di HP)
 *
 * HP (Termux): pkg install nodejs && npm install && node server.js
 * Overlay: buka http://localhost:8090/overlay.html dari browser HP / OBS Browser Source
 * WS feed: ws://localhost:8090/ws
 */

const express = require('express');
const http = require('http');
const path = require('path');
const { WebcastPushConnection } = require('tiktok-live-connector');

const args = process.argv.slice(2);
function arg(name, def = null) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : (process.env[name.replace('--', '').toUpperCase()] || def);
}

const USERNAME = (arg('--username', process.env.TIKTOK_USERNAME || '') || '').replace('@', '');
const PORT = parseInt(arg('--port', process.env.PORT || '8090'), 10);
const MOCK_MODE = process.env.MOCK_MODE === 'true' || args.includes('--mock') || !USERNAME;

console.log('='.repeat(60));
console.log('📦 TikTokBox HP - Gift Listener v0.1.0');
console.log('='.repeat(60));
console.log(`Mode     : ${MOCK_MODE ? 'MOCK (simulasi, tanpa konek TikTok)' : 'LIVE (konek room @' + USERNAME + ')'}`);
console.log(`HTTP     : http://localhost:${PORT}/`);
console.log(`Overlay  : http://localhost:${PORT}/overlay.html`);
console.log(`WS       : ws://localhost:${PORT}/ws`);
console.log('='.repeat(60));

// ---- State (mirip konsep TikTokBox: 1 diamond = 1 poin) ----
const state = {
  connected: false,
  roomId: null,
  username: USERNAME || '(mock)',
  totalDiamonds: 0,
  totalGifts: 0,
  totalLikes: 0,
  totalJoins: 0,
  points: 0, // poin terpakai = total diamonds (1:1)
  gifters: {}, // nickname -> {diamonds, count}
  feed: [], // 50 event terakhir
};

function pushFeed(ev) {
  state.feed.unshift({ t: Date.now(), ...ev });
  if (state.feed.length > 50) state.feed.pop();
  broadcast({ type: 'event', event: ev, state: publicState() });
}

function publicState() {
  const ranking = Object.entries(state.gifters)
    .map(([name, v]) => ({ name, ...v }))
    .sort((a, b) => b.diamonds - a.diamonds)
    .slice(0, 10);
  return { ...state, gifters: undefined, ranking, giftersCount: Object.keys(state.gifters).length };
}

function handleGift({ user, giftId, giftName, count = 1, diamonds = 0, repeatEnd = true }) {
  const name = user || 'anonim';
  const d = (diamonds || 0) * (count || 1);
  state.totalGifts += (count || 1);
  state.totalDiamonds += d;
  state.points += d; // 1 diamond = 1 poin (samakan TikTokBox)
  if (!state.gifters[name]) state.gifters[name] = { diamonds: 0, count: 0 };
  state.gifters[name].diamonds += d;
  state.gifters[name].count += (count || 1);
  pushFeed({ kind: 'gift', user: name, giftId, giftName, count, diamonds: d });
  console.log(`🎁 gift @${name} x${count} ${giftName || giftId} (+${d}💎, total ${state.totalDiamonds}💎)`);
}

// ---- HTTP + WS ----
const app = express();
app.use(require('cors')());
app.use(express.json());
// serve overlay statis dari ../web-overlay
app.use(express.static(path.join(__dirname, '..', 'web-overlay')));
app.get('/api/state', (req, res) => res.json(publicState()));
app.post('/api/reset', (req, res) => {
  state.totalDiamonds = 0; state.totalGifts = 0; state.totalLikes = 0;
  state.totalJoins = 0; state.points = 0; state.gifters = {}; state.feed = [];
  broadcast({ type: 'state', state: publicState() });
  res.json({ ok: true });
});
// endpoint test manual dari HP: POST /api/test-gift {user, giftName, diamonds, count}
app.post('/api/test-gift', (req, res) => {
  const { user = 'tester_hp', giftName = 'Rose', giftId = 'rose', diamonds = 1, count = 1 } = req.body || {};
  handleGift({ user, giftId, giftName, diamonds, count });
  res.json({ ok: true, state: publicState() });
});

const server = http.createServer(app);
const WebSocket = require('ws');
const wss = new WebSocket.Server({ server, path: '/ws' });
function broadcast(msg) {
  const s = JSON.stringify(msg);
  wss.clients.forEach(c => { if (c.readyState === 1) c.send(s); });
}
wss.on('connection', ws => ws.send(JSON.stringify({ type: 'state', state: publicState() })));

// ---- LIVE vs MOCK ----
async function startLive() {
  const conn = new WebcastPushConnection(USERNAME);
  conn.on('connected', s => {
    state.connected = true; state.roomId = s.roomId || s.room_id || null;
    console.log(`✅ Terkonek ke LIVE @${USERNAME} (room ${state.roomId})`);
    broadcast({ type: 'state', state: publicState() });
  });
  conn.on('disconnected', () => {
    state.connected = false;
    console.log('⚠️  Terputus dari LIVE, coba reconnect...');
    broadcast({ type: 'state', state: publicState() });
  });
  conn.on('chat', d => {
    const user = d.user?.nickname || d.user?.uniqueId || 'anonim';
    pushFeed({ kind: 'chat', user, comment: d.comment });
  });
  conn.on('gift', d => {
    // struktur tiktok-live-connector: d = {user, gift:{id,name,diamond_count}, repeat_end, ...}
    // gift berulang dikirim berkali-kali; hanya hitung saat repeat_end agar tidak dobel
    if (d.repeat_end === false) return;
    handleGift({
      user: d.user?.nickname || d.user?.uniqueId || 'anonim',
      giftId: String(d.gift?.id ?? d.giftId ?? 'gift'),
      giftName: d.gift?.name || d.giftName || 'Gift',
      count: d.repeat_count || d.count || 1,
      diamonds: d.gift?.diamond_count ?? d.diamonds ?? 0,
    });
  });
  conn.on('like', d => {
    state.totalLikes += d.count || 1;
    pushFeed({ kind: 'like', user: d.user?.nickname || 'anonim', count: d.count || 1 });
  });
  conn.on('follow', d => pushFeed({ kind: 'follow', user: d.user?.nickname || 'anonim' }));
  conn.on('share', d => pushFeed({ kind: 'share', user: d.user?.nickname || 'anonim' }));
  conn.on('join', d => {
    state.totalJoins++;
    pushFeed({ kind: 'join', user: d.user?.nickname || 'anonim' });
  });
  try {
    await conn.connect();
  } catch (e) {
    console.error('❌ Gagal konek LIVE:', e.message);
    console.error('   Pastikan username sedang LIVE. Fallback ke MOCK? jalankan: MOCK_MODE=true node server.js');
    process.exitCode = 1;
  }
}

const MOCK_GIFTS = [
  { giftName: 'Rose', giftId: 'rose', diamonds: 1 },
  { giftName: 'TikTok', giftId: 'tiktok', diamonds: 1 },
  { giftName: 'Heart', giftId: 'heart', diamonds: 5 },
  { giftName: 'GG', giftId: 'gg', diamonds: 10 },
  { giftName: 'Rocket', giftId: 'rocket', diamonds: 50 },
  { giftName: 'Lion', giftId: 'lion', diamonds: 29999 },
];
const MOCK_USERS = ['budi_gacor', 'siti_hp', 'agus_infinix', 'rani_live', 'joko_gift'];
function startMock() {
  state.connected = true;
  console.log('🎭 MOCK_MODE: simulasi gift tiap 2-4 detik. POST /api/test-gift untuk trigger manual dari HP.');
  const tick = () => {
    const g = MOCK_GIFTS[Math.floor(Math.random() * MOCK_GIFTS.length)];
    const u = MOCK_USERS[Math.floor(Math.random() * MOCK_USERS.length)];
    const count = Math.random() < 0.8 ? 1 : 1 + Math.floor(Math.random() * 9);
    handleGift({ user: u, ...g, count });
    if (Math.random() < 0.4) {
      const cu = MOCK_USERS[Math.floor(Math.random() * MOCK_USERS.length)];
      pushFeed({ kind: 'chat', user: cu, comment: ['gas bang', 'gacor!', 'naik tower!', 'avatar battle!'][Math.floor(Math.random() * 4)] });
    }
    setTimeout(tick, 2000 + Math.random() * 2500);
  };
  setTimeout(tick, 1500);
}

server.listen(PORT, () => {
  if (MOCK_MODE) startMock(); else startLive();
});
