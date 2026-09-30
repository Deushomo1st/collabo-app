// The Gaze: everyone's ideas newest first, or Shared Gaze (your network only). Filter, endless scroll. Posting happens on post.html.
// All network calls live in js/services/api.js; text goes in through textContent only.
import { live } from '/js/services/live.js';   // keeps the live socket open (yarns are acknowledged from any page) and tells us when ideas are posted or deleted
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { openGlassBlurDialog, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { postCard } from '/js/components/post-card/post-card.js';
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { currentUser, gazeFeed, gazeNewer, postGet } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const EMPTY = {
    gaze: 'Nothing here yet. Be the first to post an idea.',
    shared: 'Nothing from your network yet. Follow people, or ask them to follow you.',
};

let feed = 'gaze';
let pendingOnly = false;

// ---- the scroll: newest first, endless, and never refreshed behind your back ------------------------------------------------
// fresh = what you are reading (newest at the top); old = what you had already seen before you restarted, kept above the viewport,
// hidden, until "See old" unlocks it. Everything is remembered per feed and filter for this tab (sessionStorage), so coming back
// to the page, or flipping between tabs, shows exactly what you left; only a refresh or the "N new" pill restarts from the newest.
const MAX_OLD = 100, MAX_SAVED = 200;
let fresh = [], old = [], next = null, unlocked = false, loading = false, newCount = 0, gen = 0;
let firstBoot = true;
const reloaded = performance.getEntriesByType?.('navigation')?.[0]?.type === 'reload';

const list = () => document.getElementById('list');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
let who = '';   // whose feed this is, so signing in as someone else in the same tab never shows the last person's
const key = () => `gaze:${who}:${feed}:${pendingOnly}`;
const ids = (xs) => new Set(xs.map((p) => p.id));
const dedupe = (xs) => { const seen = new Set(); return xs.filter((p) => !seen.has(p.id) && seen.add(p.id)); };

function save() {
    try {
        if (fresh.length <= MAX_SAVED) sessionStorage.setItem(key(), JSON.stringify({ fresh, old, next, unlocked, y: Math.round(window.scrollY) }));
    } catch { /* storage full or blocked: the feed still works, it just will not be remembered */ }
}
function recall() {
    try { const s = JSON.parse(sessionStorage.getItem(key())); return s && Array.isArray(s.fresh) && Array.isArray(s.old) ? s : null; } catch { return null; }
}
let saveTimer;
window.addEventListener('scroll', () => { clearTimeout(saveTimer); saveTimer = setTimeout(save, 400); }, { passive: true });

const card = (p) => { const c = postCard(p, { onGone: () => drop(p.id) }); c.dataset.id = p.id; return c; };
const emptyNote = () => h('p', { class: 'gz-empty', text: old.length ? 'Nothing newer than what you have seen.' : EMPTY[feed] });

// The feed choices live in a pill nav in the header (the TikTok-style switcher): The Gaze, Shared Gaze, or the Gaze narrowed to ideas still open to applications.
const FEEDS = [
    { label: 'The Gaze', feed: 'gaze', open: false, icon: '<circle cx="12" cy="12" r="3"/><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/>' },
    { label: 'Shared', feed: 'shared', open: false, icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>' },
    { label: 'Open', feed: 'gaze', open: true, icon: '<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/>' },
];
const svg = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const mountFeeds = () => mountNavSelector('#filter-nav', {
    links: FEEDS.map((f) => f.label), hrefs: FEEDS.map((f, i) => '#' + i), icons: FEEDS.map((f) => svg(f.icon)),
    activeIndex: FEEDS.findIndex((f) => f.feed === feed && f.open === pendingOnly),
    onChange: (label) => { const f = FEEDS.find((x) => x.label === label); if (f.feed === feed && f.open === pendingOnly) return; save(); feed = f.feed; pendingOnly = f.open; show(); },
});

/** Builds the page from the state: the old block (hidden until unlocked), the fresh cards, and the sentinel that loads more. */
function render() {
    const oldEl = h('div', { class: 'gz-old', hidden: !(unlocked && old.length) }, ...old.map(card));
    const freshEl = h('div', { class: 'gz-fresh' }, ...fresh.map(card));
    if (!fresh.length && !next && !loading) freshEl.append(emptyNote());
    list().replaceChildren(oldEl, freshEl, h('div', { class: 'gz-sentinel' }));
    watchEnd();
    drawBar();
}

// The header (title, bell, theme) folds away while you scroll down and returns the moment you scroll up; the feed pill stays.
const placeBar = () => { document.getElementById('gz-bar').style.top = `${(document.querySelector('.sp-top')?.offsetHeight || 60) + 18}px`; };
{
    const top = document.querySelector('.sp-top');
    let lastY = window.scrollY;
    window.addEventListener('scroll', () => {
        const y = window.scrollY, dy = y - lastY;
        if (Math.abs(dy) < 6) return;   // ignore jitter
        lastY = y;
        top.classList.toggle('is-hidden', dy > 0 && y > 80);
    }, { passive: true });
    top.addEventListener('transitionend', placeBar, true);
    top.addEventListener('transitionrun', () => { const t = setInterval(placeBar, 30); setTimeout(() => clearInterval(t), 350); }, true);
}

// "N new" and "See old" share one slot at the top of the screen.
function drawBar() {
    const bar = document.getElementById('gz-bar');
    placeBar();   // just under the pinned top, however tall it is right now
    bar.replaceChildren(...[
        newCount > 0 && h('button', { class: 'gz-pill', type: 'button', text: `${newCount >= 50 ? '50+' : newCount} new`, onclick: restart }),
        old.length > 0 && !unlocked && h('button', { class: 'gz-pill gz-pill--quiet', type: 'button', text: `See old (${old.length})`, onclick: unlock }),
    ].filter(Boolean));   // replaceChildren would print a false
}

/** Entry: the first load, and flipping tabs or filters. Shows what this tab already has; fetches only when there is nothing, or after a refresh. */
async function show() {
    gen++; loading = false; newCount = 0;
    const mine = recall(), wasReload = firstBoot && reloaded;
    firstBoot = false;
    if (mine && !wasReload) {
        ({ fresh, old, next, unlocked } = mine);
        render();
        window.scrollTo({ top: mine.y || 0, behavior: 'instant' });
        return checkNewer();
    }
    // a refresh (or nothing remembered): start from the newest, and keep whatever was seen as the old block
    old = mine ? dedupe([...mine.fresh, ...mine.old]).slice(0, MAX_OLD) : [];
    fresh = []; next = null; unlocked = false;
    render();
    await more(true);
}

/** The "N new" pill: restart from the newest; everything seen so far becomes the old block. */
async function restart() {
    gen++; loading = false; newCount = 0;
    old = dedupe([...fresh, ...old]).slice(0, MAX_OLD);
    fresh = []; next = null; unlocked = false;
    render();
    window.scrollTo({ top: 0, behavior: 'instant' });
    await more(true);
}

/** Shows the old block without moving what you are reading: the page grows above you, and you scroll up into it. */
function unlock() {
    const before = document.documentElement.scrollHeight, y = window.scrollY;
    unlocked = true;
    document.querySelector('.gz-old').hidden = false;
    window.scrollTo({ top: y + document.documentElement.scrollHeight - before, behavior: 'instant' });   // not smooth: the view must not move at all
    drawBar(); save();
}

async function more(first) {
    if (loading) return;
    loading = true;
    const mine = gen;
    const sentinel = list().querySelector('.gz-sentinel');
    if (sentinel) sentinel.textContent = 'Loading…';
    try {
        const page = await gazeFeed(feed, { pending: pendingOnly, before: first ? undefined : next });
        if (mine !== gen) return;   // you moved to another tab while this loaded
        next = page.next || null;
        const have = ids(fresh);
        const items = page.items.filter((p) => !have.has(p.id));
        fresh = [...fresh, ...items];
        // a post that turns up among the fresh ones is no longer "old": take it out of the old block
        const now = ids(items);
        old = old.filter((p) => !now.has(p.id));
        list().querySelectorAll('.gz-old > [data-id]').forEach((el) => now.has(el.dataset.id) && el.remove());
        const freshEl = list().querySelector('.gz-fresh');
        freshEl.append(...items.map(card));
        freshEl.querySelector('.gz-empty')?.remove();
        if (!fresh.length) freshEl.append(emptyNote());
        if (!old.length) unlocked = false;
        drawBar(); save();
    } catch (err) {
        if (mine !== gen) return;
        if (err.status === 401) return toLogin();
        list().querySelector('.gz-sentinel')?.replaceChildren(h('span', { text: err.message + ' ' }), h('button', { class: 'gz-btn', type: 'button', text: 'Try again', onclick: () => more(first) }));
        return;
    } finally { if (mine === gen) loading = false; }
    const end = list().querySelector('.gz-sentinel');
    if (end) end.textContent = next ? '' : fresh.length ? 'You are all caught up.' : '';
    watchEnd();
}

// Loads the next page well before you reach the bottom (1500px early), so there is nothing to wait for.
let io = null;
function watchEnd() {
    io?.disconnect();
    const end = list().querySelector('.gz-sentinel');
    if (!end || !next || !('IntersectionObserver' in window)) return;
    io = new IntersectionObserver((hits) => { if (hits.some((x) => x.isIntersecting) && next && !loading) more(false); }, { rootMargin: '0px 0px 1500px 0px' });
    io.observe(end);   // observing fires at once if the end is already near, so a short page keeps filling
}

/** A deleted idea disappears from wherever it is showing, and from what is remembered. */
function drop(id) {
    fresh = fresh.filter((p) => p.id !== id); old = old.filter((p) => p.id !== id);
    document.querySelectorAll(`#list [data-id="${CSS.escape(id)}"]`).forEach((el) => el.remove());
    if (!old.length) unlocked = false;
    drawBar(); save();
}

async function checkNewer() {
    const top = fresh.slice(0, 5).map((p) => p.id), mine = gen;
    if (!top.length) return;
    try {
        const { count } = await gazeNewer(feed, { pending: pendingOnly, top });
        if (mine !== gen) return;
        newCount = count; drawBar();
    } catch { /* offline or signed out: the pill just stays as it was */ }
}
let newerTimer;
const soon = () => { clearTimeout(newerTimer); newerTimer = setTimeout(checkNewer, 500 + Math.random() * 2500); };   // the jitter spreads a crowd's questions out

/** A shared link (gaze.html?post=ID) opens that one post over the feed. */
async function openLinkedPost() {
    const id = new URLSearchParams(location.search).get('post');
    if (!id) return;
    try {
        const p = await postGet(id);
        const { panel, close } = await openGlassBlurDialog({ size: 'lg', label: 'Post', html: '<div class="gz-linked"></div>' });
        panel.querySelector('.gz-linked').append(postCard(p, { onGone: () => { close(); drop(id); } }));
    } catch (err) { toast(err.message); }
    history.replaceState(null, '', location.pathname);   // a refresh should not pop it open again
}

// The bottom nav: back to the top of the Gaze, Yarns, a plus to post, and your profile.
async function mountBottom(username) {
    const pages = [
        ['Gaze', '#top', svg('<path d="M3 10.5 12 3l9 7.5V21H3z"/>')],
        ['Yarns', '/HTML-pages/yarnspaces.html', svg('<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>')],
        ['Post', '#post', svg('<path d="M12 5v14M5 12h14"/>')],
        ['Profile', profileHref(username), svg('<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>')],
    ];
    const nav = await mountNavSelector('#bottom-nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, activeIndex: 0,
        links: pages.map((p) => p[0]), hrefs: pages.map((p) => p[1]), icons: pages.map((p) => p[2]),
        onChange: (label, href) => {
            if (label === 'Gaze') return window.scrollTo({ top: 0, behavior: 'smooth' });
            if (label === 'Post') { nav.setActive(0); location.href = '/HTML-pages/post.html'; return; }   // the post page is a page of its own
            location.href = href;
        },
    });
}

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    who = me.username;
    mountFeeds();
    mountBottom(me.username);
    live.on('gaze', soon);
    live.on('post-gone', (s) => drop(s.post));
    live.onResync(soon);
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') soon(); });
    setInterval(() => { if (!live.connected && document.visibilityState === 'visible') checkNewer(); }, 60_000);   // a safety net while the socket is down
    show();
    try { const msg = sessionStorage.getItem('collaboToast'); if (msg) { sessionStorage.removeItem('collaboToast'); toast(msg); } } catch { /* just no message */ }
    openLinkedPost();
}

preloadGlassBlurDialog();
mountThemeSwitcher('#theme-slot', { inline: true });
boot();
