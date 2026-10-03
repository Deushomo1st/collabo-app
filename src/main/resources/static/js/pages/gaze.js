// The Gaze: everyone's ideas newest first, or Shared Gaze (your network only). Filter, endless scroll. Posting happens on create-post.html.
// All network calls live in js/services/api.js; text goes in through textContent only.
import { live } from '/js/services/live.js';   // keeps the live socket open (yarns are acknowledged from any page) and tells us when ideas are posted or deleted
import { openGlassBlurDialog, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { postCard } from '/js/components/post-card/post-card.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { currentUser, gazeFeed, gazeNewer, gazeSearch, gazePeople, postGet, profileGet, following } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { skeletonCards } from '/js/services/skeleton.js';
import { historyAdd, historyDrop } from '/js/services/history.js';

const EMPTY = {
    gaze: 'Nothing here yet. Be the first to post an idea.',
    shared: 'Nothing from your network yet. Follow people, or ask them to follow you.',
};

let feed = 'gaze';
let pendingOnly = false;

// ---- the scroll: newest first, endless, and never refreshed behind your back ------------------------------------------------
// fresh = what you are reading (newest at the top). Everything is remembered per feed and filter for this tab (sessionStorage), so coming back
// to the page, or flipping between tabs, shows exactly what you left; only a refresh, posting, or the Latest button restarts from the newest,
// and what you had been reading then moves to History (Settings > Privacy).
const MAX_SAVED = 200;
let fresh = [], next = null, loading = false, newCount = 0, gen = 0;
let firstBoot = true;
const reloaded = performance.getEntriesByType?.('navigation')?.[0]?.type === 'reload';
// Posting leaves a note (create-post.js): the next time the Gaze shows, it restarts from the newest, so your own post is the first card.
const refreshAsked = () => { try { const v = sessionStorage.getItem('collaboFeedRefresh'); sessionStorage.removeItem('collaboFeedRefresh'); return !!v; } catch { return false; } };

const list = () => document.getElementById('list');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
let who = '';   // whose feed this is, so signing in as someone else in the same tab never shows the last person's
const key = () => `gaze:${who}:${feed}:${pendingOnly}`;
const ids = (xs) => new Set(xs.map((p) => p.id));

function save() {
    try {
        if (fresh.length <= MAX_SAVED) sessionStorage.setItem(key(), JSON.stringify({ fresh, next, y: Math.round(window.scrollY) }));
    } catch { /* storage full or blocked: the feed still works, it just will not be remembered */ }
}
function recall() {
    try { const s = JSON.parse(sessionStorage.getItem(key())); return s && Array.isArray(s.fresh) ? s : null; } catch { return null; }
}
let saveTimer;
window.addEventListener('scroll', () => { clearTimeout(saveTimer); saveTimer = setTimeout(save, 400); }, { passive: true });
window.addEventListener('pagehide', () => { clearTimeout(saveTimer); save(); });   // leaving mid-scroll (a quick tap on a post) still remembers the exact spot

const card = (p) => { const c = postCard(p, { onGone: () => drop(p.id) }); c.dataset.id = p.id; return c; };
const emptyNote = () => h('p', { class: 'gz-empty', text: EMPTY[feed] });

// The feed choices are tabs in the header: The Gaze, Shared Gaze, or the Gaze narrowed to ideas still open to applications.
const FEEDS = [
    { label: 'The Gaze', feed: 'gaze', open: false, icon: '<circle cx="12" cy="12" r="3"/><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/>' },
    { label: 'Shared', feed: 'shared', open: false, icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>' },
    { label: 'Open', feed: 'gaze', open: true, icon: '<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/>' },
];
const svg = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;

// TikTok-style: plain text tabs in the middle of the header, the live one bold with a short underline. The three feeds go round: the live one is
// always in the middle with the other two at the edges; a swipe sideways (or a tap on an edge one) brings its neighbour to the middle and the third rotates round.
// The search icon swaps the tabs for a search box.
const mountFeeds = () => {
    const nav = document.getElementById('filter-nav');
    const live = (f) => f.feed === feed && f.open === pendingOnly;
    const at = () => Math.max(0, FEEDS.findIndex(live));
    const of = (d) => FEEDS[(at() + d + FEEDS.length) % FEEDS.length];
    const pick = (f, dir) => { if (live(f)) return; save(); feed = f.feed; pendingOnly = f.open; draw(dir); show(); };
    const draw = (dir = '') => {
        nav.dataset.dir = dir;
        nav.replaceChildren(...[-1, 0, 1].map((d) => { const f = of(d); return h('button', { class: 'tt-tab', type: 'button', role: 'tab', 'aria-selected': String(d === 0), text: f.label, onclick: () => pick(f, d > 0 ? 'next' : 'prev') }); }));
    };
    let x0 = 0, swiped = false;
    nav.addEventListener('pointerdown', (e) => { x0 = e.clientX; swiped = false; });
    nav.addEventListener('pointerup', (e) => { if (Math.abs(e.clientX - x0) < 30) return; swiped = true; const d = e.clientX < x0 ? 1 : -1; pick(of(d), d > 0 ? 'next' : 'prev'); });
    nav.addEventListener('click', (e) => { if (swiped) { e.stopPropagation(); swiped = false; } }, true);   // a swipe that ends on a tab is not also a tap on it
    // The same swipe anywhere on the feed: a clear sideways stroke (not a scroll that drifted) moves to the neighbouring feed. Typing and the tab bar keep their own gestures.
    let t0 = null;
    document.addEventListener('touchstart', (e) => { const t = e.touches[0]; t0 = e.touches.length === 1 && !e.target.closest('input, textarea, select, #filter-nav, [role="dialog"]') ? { x: t.clientX, y: t.clientY } : null; }, { passive: true });
    document.addEventListener('touchend', (e) => {
        const t = e.changedTouches[0], s = t0; t0 = null;
        if (!s || searching) return;
        const dx = t.clientX - s.x, dy = t.clientY - s.y;
        if (Math.abs(dx) > 70 && Math.abs(dx) > 2 * Math.abs(dy)) { const d = dx < 0 ? 1 : -1; pick(of(d), d > 0 ? 'next' : 'prev'); }
    }, { passive: true });
    draw();
};

let searching = false, searchTimer, searchGen = 0;
function setSearching(on) {
    searching = on;
    document.getElementById('filter-nav').hidden = on;
    document.getElementById('tt-end').hidden = on;
    document.querySelector('.nav-menu')?.toggleAttribute('hidden', on);
    document.getElementById('tt-search').hidden = !on;
    document.getElementById('tt-filters').hidden = !on;
    if (on) { drawFilters(); const q = document.getElementById('tt-q'); q.value = ''; q.focus(); idle(); }
    else { searchGen++; show(); }
}
// Before anything is typed: the people you follow as a row of round pictures; the name shows on hover (and as the title on touch).
async function idle() {
    const mine = ++searchGen;
    list().replaceChildren(h('p', { class: 'gz-empty', text: 'Search people, or ideas by title, words or #hashtag.' }));
    const people = await following(who).catch(() => []);
    if (mine !== searchGen || !people.length) return;
    list().prepend(h('div', { class: 'gz-circles', 'aria-label': 'People you follow' }, ...people.map((u) =>
        h('a', { class: 'gz-circle', href: profileHref(u.username), 'data-name': u.username }, face(u.username)))));
}
// Filters under the box, like TikTok: Top (people, then the most viewed ideas), Users, Ideas (newest first).
const FILTERS = [['top', 'Top'], ['users', 'Users'], ['ideas', 'Ideas']];
let filter = 'top';
const drawFilters = () => document.getElementById('tt-filters').replaceChildren(...FILTERS.map(([key, label]) =>
    h('button', { class: 'tt-tab', type: 'button', role: 'tab', 'aria-selected': String(filter === key), text: label, onclick: () => { filter = key; drawFilters(); runSearch(); } })));
const personRow = (u) => h('a', { class: 'gz-person sp-glass', href: profileHref(u.username) }, face(u.username),
    h('span', {}, h('strong', { text: u.username }), u.preferredTitle && h('small', { text: u.preferredTitle })));
async function runSearch() {
    const text = document.getElementById('tt-q').value.trim(), mine = ++searchGen;
    if (text.length < 2) return void idle();
    try {
        const wantPeople = filter !== 'ideas', wantIdeas = filter !== 'users';
        const [people, ideas] = await Promise.all([wantPeople ? gazePeople(text) : [], wantIdeas ? gazeSearch(text, filter === 'top' ? 'top' : '') : { items: [] }]);
        if (mine !== searchGen) return;   // a newer search is already on its way
        const rows = [...people.slice(0, filter === 'top' ? 3 : 10).map(personRow), ...ideas.items.map(card)];
        list().replaceChildren(...(rows.length ? rows : [h('p', { class: 'gz-empty', text: `Nothing matches "${text}".` })]));
    } catch (e) { if (mine === searchGen) list().replaceChildren(h('p', { class: 'gz-empty', text: e.message })); }
}
document.getElementById('tt-find').addEventListener('click', () => setSearching(true));
document.getElementById('tt-cancel').addEventListener('click', () => setSearching(false));
document.getElementById('tt-search').addEventListener('submit', (e) => { e.preventDefault(); clearTimeout(searchTimer); runSearch(); });
document.getElementById('tt-q').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 300); });
document.getElementById('tt-q').addEventListener('keydown', (e) => { if (e.key === 'Escape') setSearching(false); });

/** Builds the page from the state: the cards and the sentinel that loads more. */
function render() {
    const freshEl = h('div', { class: 'gz-fresh' }, ...fresh.map(card));
    if (!fresh.length && !next && !loading) freshEl.append(emptyNote());
    list().replaceChildren(freshEl, h('div', { class: 'gz-sentinel' }));
    watchEnd();
    markLatest();
}

// The header (tabs, search, bell) stays pinned; its blurred backing fades in once the feed has moved under it.
{
    const top = document.querySelector('.sp-top');
    const mark = () => top.classList.toggle('is-scrolled', window.scrollY > 8);
    window.addEventListener('scroll', mark, { passive: true });
    mark();   // a restored scroll position starts with the backing already on
}

// "Latest": a button at the bottom that jumps to the newest post, loading the new ones first when there are some. It shows once the newest is more than a screen away.
const headerH = () => document.querySelector('.sp-top').offsetHeight || 52;
// While new posts wait, the faces of up to three of their authors sit in it, people you follow first.
const faces = h('span', { class: 'gz-faces' });
const latest = h('button', { class: 'gz-latest', type: 'button', hidden: true, onclick: () => {
    if (newCount > 0) return restart();
    const f = list().querySelector('.gz-fresh');
    window.scrollTo({ top: f ? f.getBoundingClientRect().top + window.scrollY - headerH() - 8 : 0, behavior: 'smooth' });
} }, faces, h('span', { text: 'Latest' }));
document.body.append(latest);
let newAuthors = [], facesShown = '';
function markLatest() {
    const f = list().querySelector('.gz-fresh'), top = f ? f.getBoundingClientRect().top - headerH() : 0;
    latest.hidden = !(newCount > 0 || Math.abs(top) > window.innerHeight);
    latest.title = newCount > 0 ? `${newCount >= 50 ? '50+' : newCount} new` : '';
    const names = newCount > 0 ? newAuthors : [];
    if (names.join() !== facesShown) { facesShown = names.join(); faces.replaceChildren(...names.map((n) => face(n))); }
}
let followed = null;   // who you follow, asked once
async function authorsOfNew(mine) {
    followed ??= following(who).then((us) => new Set(us.map((u) => u.username))).catch(() => new Set());
    const [page, set] = await Promise.all([gazeFeed(feed, { pending: pendingOnly }), followed]);
    if (mine !== gen) return [];
    const have = ids(fresh), names = [];
    for (const p of page.items) if (!have.has(p.id) && !p.anonymous && !p.mine && p.author?.username && !names.includes(p.author.username)) names.push(p.author.username);
    return [...names.filter((n) => set.has(n)), ...names.filter((n) => !set.has(n))].slice(0, 3);
}
window.addEventListener('scroll', markLatest, { passive: true });
window.addEventListener('pageshow', (e) => { if (e.persisted && refreshAsked()) restart(); });   // back to a kept page after posting

/** Entry: the first load, and flipping tabs or filters. Shows what this tab already has; fetches only when there is nothing, or after a refresh. */
async function show() {
    gen++; loading = false; newCount = 0; newAuthors = [];
    const mine = recall(), wasReload = firstBoot && (reloaded || refreshAsked());
    firstBoot = false;
    if (mine && !wasReload) {
        ({ fresh, next } = mine);
        render();
        window.scrollTo({ top: mine.y || 0, behavior: 'instant' });
        return checkNewer();
    }
    // a refresh (or nothing remembered): start from the newest; what was being read goes to History
    if (mine) historyAdd(who, mine.fresh);
    fresh = []; next = null;
    render();
    await more(true);
}

/** The Latest button with new posts waiting: restart from the newest; everything seen so far goes to History. */
async function restart() {
    gen++; loading = false; newCount = 0; newAuthors = [];
    historyAdd(who, fresh);
    fresh = []; next = null;
    render();
    window.scrollTo({ top: 0, behavior: 'instant' });
    await more(true);
}

async function more(first) {
    if (loading) return;
    loading = true;
    const mine = gen;
    const sentinel = list().querySelector('.gz-sentinel');
    if (sentinel && !first) sentinel.textContent = 'Loading…';
    const holders = first && !fresh.length ? skeletonCards(3) : [];   // the first load shows the shape of the feed, not empty space
    list().querySelector('.gz-fresh')?.append(...holders);
    try {
        const page = await gazeFeed(feed, { pending: pendingOnly, before: first ? undefined : next });
        if (mine !== gen) return;   // you moved to another tab while this loaded
        next = page.next || null;
        const have = ids(fresh);
        const items = page.items.filter((p) => !have.has(p.id));
        fresh = [...fresh, ...items];
        const freshEl = list().querySelector('.gz-fresh');
        freshEl.append(...items.map(card));
        freshEl.querySelector('.gz-empty')?.remove();
        if (!fresh.length) freshEl.append(emptyNote());
        markLatest(); save();
    } catch (err) {
        if (mine !== gen) return;
        if (err.status === 401) return toLogin();
        list().querySelector('.gz-sentinel')?.replaceChildren(h('span', { text: err.message + ' ' }), h('button', { class: 'gz-btn', type: 'button', text: 'Try again', onclick: () => more(first) }));
        return;
    } finally { holders.forEach((e) => e.remove()); if (mine === gen) loading = false; }
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
    fresh = fresh.filter((p) => p.id !== id); historyDrop(who, id);
    document.querySelectorAll(`#list [data-id="${CSS.escape(id)}"]`).forEach((el) => el.remove());
    markLatest(); save();
}

async function checkNewer() {
    const top = fresh.slice(0, 5).map((p) => p.id), mine = gen;
    if (!top.length) return;
    try {
        const { count } = await gazeNewer(feed, { pending: pendingOnly, top });
        if (mine !== gen) return;
        newCount = count; markLatest();
        if (count > 0) { const names = await authorsOfNew(mine).catch(() => []); if (mine === gen) { newAuthors = names; markLatest(); } }   // the count shows at once, the faces join when they arrive
    } catch { /* offline or signed out: the pill just stays as it was */ }
}
let newerTimer;
const soon = () => { clearTimeout(newerTimer); newerTimer = setTimeout(checkNewer, 500 + Math.random() * 2500); };   // the jitter spreads a crowd's questions out

/** An older shared link (gaze.html?post=ID) goes to the post's own page. */
function openLinkedPost() {
    const id = new URLSearchParams(location.search).get('post');
    if (id) location.replace(`/HTML-pages/view-post.html?id=${encodeURIComponent(id)}`);
}

const mountBottom = (username) => mountMainNav(username, 'Gaze', { onGaze: () => window.scrollTo({ top: 0, behavior: 'smooth' }) });   // Gaze again goes back to the top

// Someone who skipped the welcome flow keeps a gentle reminder until they add a photo or a bio (or close it for this tab).
async function nudge(name) {
    try {
        if (sessionStorage.getItem('collaboNudge')) return;
        const p = await profileGet(name);
        if (p.bio || p.avatarVersion) return;
        const bar = h('div', { class: 'gz-nudge sp-glass' },
            h('a', { href: '/HTML-pages/profile.html', text: 'Finish your profile: a photo and a short bio help people say yes to you.' }),
            h('button', { type: 'button', 'aria-label': 'Dismiss', text: '×', onclick: () => { bar.remove(); try { sessionStorage.setItem('collaboNudge', '1'); } catch { /* it just comes back */ } } }));
        list().before(bar);
    } catch { /* a reminder is never worth an error */ }
}

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    if (me.needsWelcome) return location.replace('/HTML-pages/welcome.html');   // first visit: set up before browsing
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
    nudge(me.username);
}

preloadGlassBlurDialog();
boot();
