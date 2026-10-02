// Counts a view when a post card is mostly on screen in a feed. Ids are collected and sent in one request every couple of seconds,
// so scrolling a long feed stays well under the write limit. A post is reported once per page load; the server counts each person once.
import { postSeen } from '/js/services/api.js';

const FLUSH_MS = 2000;
const sent = new Set();
let pending = new Set();
let timer = null;

function flush() {
    clearTimeout(timer); timer = null;
    const ids = [...pending];
    pending = new Set();
    if (ids.length) postSeen(ids).catch(() => { /* a missed view is not worth bothering anyone */ });
}

const watcher = 'IntersectionObserver' in window ? new IntersectionObserver((hits) => {
    for (const hit of hits) {
        if (!hit.isIntersecting) continue;
        watcher.unobserve(hit.target);
        const id = hit.target.dataset.seenId;
        if (sent.has(id)) continue;   // the same post drawn twice (a redrawn feed) is still one report
        sent.add(id); pending.add(id);
    }
    if (pending.size && !timer) timer = setTimeout(flush, FLUSH_MS);
}, { threshold: 0.5 }) : null;

// Leaving the page (opening a post, switching tabs) sends what is waiting instead of dropping it.
addEventListener('pagehide', flush);
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') flush(); });

/** Watches a card; its post is reported the first time half of it is on screen. */
export function watchSeen(el, id) {
    if (!watcher || sent.has(id)) return;
    el.dataset.seenId = id;
    watcher.observe(el);
}
