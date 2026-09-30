// The Gaze: everyone's ideas newest first, or Shared Gaze (your network only). Compose, filter, endless scroll.
// All network calls live in js/services/api.js; text goes in through textContent only.
import { live } from '/js/services/live.js';   // keeps the live socket open (yarns are acknowledged from any page) and tells us when ideas are posted or deleted
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { openGlassBlurDialog, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { postCard } from '/js/components/post-card/post-card.js';
import { openMine } from '/js/components/applications/applications.js';
import { openCollaborations } from '/js/components/collaborators/collaborators.js';
import { currentUser, gazeFeed, gazeNewer, postCreate } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const MAX_TITLE = 120, MAX_BODY = 2000;
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

function controls() {
    const tab = (id, label) => h('button', { class: 'gz-tab', role: 'tab', type: 'button', 'aria-selected': String(feed === id), text: label,
        onclick: () => { if (feed === id) return; save(); feed = id; drawControls(); show(); } });
    const pending = h('input', { type: 'checkbox', checked: pendingOnly, onchange: (e) => { save(); pendingOnly = e.target.checked; show(); } });
    return [h('div', { class: 'gz-tabs', role: 'tablist' }, tab('gaze', 'The Gaze'), tab('shared', 'Shared Gaze')),
        h('label', { class: 'gz-filter' }, pending, ' Open to applications only')];
}
const drawControls = () => document.getElementById('controls').replaceChildren(...controls());

/** Builds the page from the state: the old block (hidden until unlocked), the fresh cards, and the sentinel that loads more. */
function render() {
    const oldEl = h('div', { class: 'gz-old', hidden: !(unlocked && old.length) }, ...old.map(card));
    const freshEl = h('div', { class: 'gz-fresh' }, ...fresh.map(card));
    if (!fresh.length && !next && !loading) freshEl.append(emptyNote());
    list().replaceChildren(oldEl, freshEl, h('div', { class: 'gz-sentinel' }));
    watchEnd();
    drawBar();
}

// "N new" and "See old" share one slot at the top of the screen.
function drawBar() {
    const bar = document.getElementById('gz-bar');
    bar.style.top = `${(document.querySelector('.sp-top')?.offsetHeight || 60) + 18}px`;   // just under the header, however tall it wraps
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

async function compose() {
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: 'New idea', html:
        '<h3 class="glass-blur-dialog__title">Post an idea</h3><form class="sp-form"></form>' });
    const title = h('input', { class: 'sp-input', id: 'gz-title', maxlength: MAX_TITLE, required: true, placeholder: 'What are you building?' });
    const body = h('textarea', { class: 'sp-input', id: 'gz-body', rows: 6, maxlength: MAX_BODY, required: true, placeholder: 'Who do you need, and what will you make together?' });
    const by = h('input', { class: 'sp-input', id: 'gz-by', type: 'datetime-local' });
    const err = h('p', { class: 'gz-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(h('label', { for: 'gz-title' }, 'Title', title), h('label', { for: 'gz-body' }, 'Description', body),
        h('label', { for: 'gz-by' }, 'Applications close (optional)', by), err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Post')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await postCreate(title.value, body.value, by.value ? new Date(by.value).toISOString() : null);
            close(); toast('Posted. Your own ideas live on your profile, not in your Gaze.');
        } catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    who = me.username;
    document.getElementById('me-link').href = profileHref(me.username);
    drawControls();
    live.on('gaze', soon);
    live.on('post-gone', (s) => drop(s.post));
    live.onResync(soon);
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') soon(); });
    setInterval(() => { if (!live.connected && document.visibilityState === 'visible') checkNewer(); }, 60_000);   // a safety net while the socket is down
    show();
}

preloadGlassBlurDialog();
mountThemeSwitcher('#theme-slot', { inline: true });
document.getElementById('compose-btn').addEventListener('click', compose);
document.getElementById('mine-btn').addEventListener('click', openMine);
document.getElementById('collab-btn').addEventListener('click', openCollaborations);
boot();
