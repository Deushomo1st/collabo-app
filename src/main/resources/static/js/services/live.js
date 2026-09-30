// Live: the one WebSocket a signed-in page keeps to the server, and the signals that arrive on it.
//
// A signal only says WHAT changed ({ t: 'yarn', thread, yarn }); the view that cares refetches that one thing over the normal API,
// silently, without reloading the page. Signals can be lost (a dropped connection, a sleeping laptop), so every view also
// resyncs when the socket comes back, and keeps a slow poll as a safety net while it is down.
//
// Import this for its side effect on any signed-in page: it connects, acknowledges delivery of yarns site-wide (so the sender's
// second tick appears as soon as you are anywhere on the site), and reconnects by itself.
//
//   import { live } from '/js/services/live.js';
//   const off = live.on('yarn', (s) => { if (s.thread === id) refresh(); });   // off() to stop
//   live.onResync(refresh);                                                     // after a reconnect
//   live.connected                                                              // true while the socket is open

const PING_MS = 25_000, PONG_WAIT_MS = 10_000, BACKOFF_MAX_MS = 30_000;

const handlers = new Map();   // signal type -> Set of functions
const resyncs = new Set();
let ws = null, open = false, everOpen = false, tries = 0, pingTimer = null, pongTimer = null, retryTimer = null;

const sub = (set, fn) => { set.add(fn); return () => set.delete(fn); };
const safe = (fn, arg) => { try { fn(arg); } catch (e) { console.error('live handler failed', e); } };

function url() { return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/live`; }

function connect() {
    if (ws || !('WebSocket' in window)) return;
    try { ws = new WebSocket(url()); } catch { return schedule(); }
    ws.addEventListener('open', () => {
        open = true; tries = 0;
        if (everOpen) resyncs.forEach((fn) => safe(fn));   // anything may have changed while we were away
        everOpen = true;
        pingTimer = setInterval(ping, PING_MS);
    });
    ws.addEventListener('message', (e) => {
        let s; try { s = JSON.parse(e.data); } catch { return; }
        if (s.t === 'pong') return void clearTimeout(pongTimer);
        handlers.get(s.t)?.forEach((fn) => safe(fn, s));
    });
    ws.addEventListener('close', () => {
        open = false; ws = null;
        clearInterval(pingTimer); clearTimeout(pongTimer);
        schedule();
    });
    ws.addEventListener('error', () => { /* 'close' follows and handles it */ });
}

function ping() {
    if (!send({ t: 'ping' })) return;
    clearTimeout(pongTimer);
    pongTimer = setTimeout(() => ws?.close(), PONG_WAIT_MS);   // no answer: the connection is dead even if it does not say so
}

// Exponential backoff with jitter, so a restarted server is not hit by every tab at once.
function schedule() {
    clearTimeout(retryTimer);
    const wait = Math.min(BACKOFF_MAX_MS, 1000 * 2 ** tries++) * (0.5 + Math.random() / 2);
    retryTimer = setTimeout(connect, wait);
}

function send(frame) {
    if (!open || ws?.readyState !== WebSocket.OPEN) return false;
    ws.send(JSON.stringify(frame));
    return true;
}

export const live = {
    on: (type, fn) => sub(handlers.get(type) ?? handlers.set(type, new Set()).get(type), fn),
    onResync: (fn) => sub(resyncs, fn),
    send,
    get connected() { return open; },
};

// Delivery: the moment a yarn reaches this browser, tell the server. That is what turns the sender's single tick into two,
// whichever page of the site you happen to be on.
// A burst of yarns in one room is acknowledged once, by its newest (the mark is "delivered up to here"), so the socket is never flooded.
const toAck = new Map();   // thread -> newest yarn id seen, not yet acknowledged
live.on('yarn', (s) => {
    if (!toAck.size) setTimeout(() => { for (const [thread, yarn] of toAck) send({ t: 'delivered', thread, yarn }); toAck.clear(); }, 120);
    toAck.set(s.thread, s.yarn);
});

// A tab that was asleep reconnects as soon as it is looked at, instead of waiting out a backoff.
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && !open && !ws) { tries = 0; connect(); } });
window.addEventListener('online', () => { if (!open && !ws) { tries = 0; connect(); } });

connect();
