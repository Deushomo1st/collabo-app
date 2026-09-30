// The admin console's live socket. The console has a key, not a session, so it trades the key for a one-time ticket
// (POST /api/admin/live/ticket) and opens /ws/admin?ticket=... The server only says { t: 'queue' } when the investigation
// queue changed; the view refetches. Reconnects with backoff; `connected()` tells the caller whether to keep a slow poll as a net.
import * as api from './admin-api.js';

export function watchQueue(onChange) {
    let ws = null, open = false, tries = 0, stopped = false, retry, ping;

    async function connect() {
        if (stopped) return;
        try {
            const { ticket } = await api.liveTicket();
            if (stopped) return;
            ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/admin?ticket=${encodeURIComponent(ticket)}`);
        } catch { return again(); }
        ws.onopen = () => { const back = tries > 0; open = true; tries = 0; ping = setInterval(() => ws?.send('{"t":"ping"}'), 25_000); if (back) onChange(); };
        ws.onmessage = (e) => { try { if (JSON.parse(e.data).t === 'queue') onChange(); } catch { /* not ours */ } };
        ws.onclose = () => { open = false; ws = null; clearInterval(ping); again(); };
    }
    const again = () => { if (!stopped) retry = setTimeout(connect, Math.min(30_000, 1000 * 2 ** tries++) * (0.5 + Math.random() / 2)); };

    connect();
    return { connected: () => open, stop() { stopped = true; clearTimeout(retry); clearInterval(ping); ws?.close(); } };
}
