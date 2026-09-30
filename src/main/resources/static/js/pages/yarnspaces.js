// Yarnspaces: MySpace (1-on-1), WeSpace (1-to-many) and Workspaces, plus Archive and Blocked.
// Reached through the "Yarns" label. All network calls live in js/services/api.js.
// Text goes in through textContent only (h() never sets innerHTML for user text).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { createActionBanner, preloadActionBanner } from '/js/components/action-banner/action-banner.js';
import { openGlassBlurDialog, glassBlurConfirm, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { createYarnThread, preloadYarnThread } from '/js/components/yarn-thread/yarn-thread.js';
import { live } from '/js/services/live.js';
import {
    logoutUser, yarnMe, yarnDirectory, yarnThreads, yarnStartMySpace, yarnHistory, yarnSend,
    yarnMarkRead, yarnPrefs, yarnRespond, yarnBlocked, yarnBlock, yarnUnblock, yarnReport,
} from '/js/services/api.js';
import { SECTIONS, TIER_LABEL, inSection, unreadTotal, matches, ago, hue } from '/js/pages/yarnspaces-data.js';

let me = null;
let inbox = [], archived = [], blocked = [];
let query = '';
let openThread = null;      // the thread being read, if any
let threadView = null;      // its yarn-thread instance
let lastSection = 'all';
let nav;

// ---- tiny DOM helper -------------------------------------------------------
function h(tag, props = {}, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'text') el.textContent = v;
        else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
        else if (k === 'style') el.style.cssText = v;
        else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) if (kid != null && kid !== false) el.append(kid);
    return el;
}
const svg = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const ICON = {
    all: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    myspace: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    wespace: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    workspace: '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/>',
    archive: '<rect x="2" y="3" width="20" height="5" rx="1"/><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"/><path d="M10 12h4"/>',
    blocked: '<circle cx="12" cy="12" r="10"/><line x1="4.9" y1="4.9" x2="19.1" y2="19.1"/>',
};

function toast(text) {
    const el = document.getElementById('toast');
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2600);
}
const face = (name, cls = '') => h('span', { class: `sp-avatar ${cls}`, style: `--hue:${hue(name)}`, title: name, text: name[0].toUpperCase() });

// ---- routing ---------------------------------------------------------------
const PREF_KEY = 'collaboYarnDefault';   // which section opens when Yarns is entered from the Dash (no #hash)
const savedDefault = () => { try { return localStorage.getItem(PREF_KEY); } catch { return null; } };
const threadIdInHash = () => (location.hash.startsWith('#t/') ? location.hash.slice(3) : null);
const currentSection = () => SECTIONS.find((s) => location.hash === '#' + s.id)
    || SECTIONS.find((s) => !location.hash && s.id === savedDefault()) || SECTIONS[0];
const go = (hash) => { if (location.hash === hash) route(); else location.hash = hash; };

function setHeader(t) {
    const n = unreadTotal(inbox);
    document.getElementById('header-title').textContent = t ? t.name : 'Yarns';
    document.getElementById('header-tag').textContent = t ? TIER_LABEL[t.tier] : 'Yarnspaces';
    const sub = document.getElementById('header-sub');
    if (t) {   // each member links to their profile
        sub.replaceChildren(...t.members.flatMap((m, i) => [i ? ', ' : '', h('a', { href: `/HTML-pages/profile.html?u=${encodeURIComponent(m.username)}`, text: m.username })]).filter(Boolean));
    } else sub.textContent = n ? `${n} unread ${n === 1 ? 'yarn' : 'yarns'}` : 'All caught up';
    document.getElementById('search').hidden = !!t;
}

async function loadAll() {
    [inbox, archived, blocked] = await Promise.all([yarnThreads('inbox'), yarnThreads('archived'), yarnBlocked()]);
}

function stopThread() { threadView?.destroy(); threadView = null; openThread = null; }

async function route() {
    stopThread();
    const id = threadIdInHash();
    if (!id) { setHeader(null); nav?.setActive(SECTIONS.indexOf(currentSection())); renderSection(); return; }
    try {
        if (![...inbox, ...archived].some((t) => t.id === id)) await loadAll();
    } catch (err) { return fatal(err); }
    const t = [...inbox, ...archived].find((x) => x.id === id);
    if (!t) { setHeader(null); return message('That thread is gone, or it is not yours.'); }
    openThread = t;
    setHeader(t);
    renderThread();
}

// replaceChildren would print a literal "null" for empty slots, so drop them
const fill = (root, ...kids) => root.replaceChildren(...kids.flat().filter(Boolean));

function message(text) { document.getElementById('section').replaceChildren(h('p', { class: 'yn-empty', text })); }

function fatal(err) {
    if (err.status === 401) return toLogin();
    message(err.message || 'Could not load your yarns.');
    document.getElementById('section').append(h('button', { class: 'sp-btn', type: 'button', text: 'Try again', onclick: start }));
}

// ---- signed out -> the login page, then back here ----------------------------
function toLogin() {
    location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search + location.hash));
}

// ---- lists -----------------------------------------------------------------
function row(t, quick) {
    const others = t.members.filter((m) => m.id !== me.id);
    const shown = (others.length ? others : t.members).slice(0, 3);
    const flags = [t.pinned && 'Pinned', t.muted && 'Muted'].filter(Boolean).join(' · ');
    return h('div', { class: 'yn-item' },
        h('button', { class: `yn-row ${t.unread ? 'is-unread' : ''}`, type: 'button', onclick: () => { location.hash = '#t/' + t.id; } },
            h('span', { class: 'yn-faces' }, ...shown.map((m) => face(m.username, 'sp-avatar--sm'))),
            h('span', { class: 'yn-body' },
                h('span', { class: 'yn-line' },
                    h('strong', { text: t.name }),
                    h('span', { class: 'sp-tag', text: TIER_LABEL[t.tier] }),
                    flags && h('span', { class: 'yn-flags', text: flags }),
                    h('time', { text: ago(t.lastAt) })),
                h('span', { class: 'yn-last', text: `${t.lastSender}: ${t.lastBody || ''}` })),
            t.unread ? h('span', { class: 'yn-badge', 'aria-label': `${t.unread} unread yarns`, text: String(t.unread) }) : null),
        h('button', { class: 'yn-quick', type: 'button', title: quick.label, onclick: quick.run, text: quick.label }));
}

async function act(fn, done) {
    try { await fn(); await loadAll(); if (done) toast(done); setHeader(null); renderSection(); }
    catch (err) { toast(err.message); }
}

const archiveQuick = (t) => ({
    label: t.archived ? 'Restore' : 'Archive',
    run: () => act(() => yarnPrefs(t.id, { archived: !t.archived }), t.archived ? 'Restored to your inbox.' : 'Archived. A new yarn brings it back.'),
});

function renderSection() {
    const sec = currentSection();
    lastSection = sec.id;
    const root = document.getElementById('section');
    if (sec.id === 'blocked') return renderBlocked(root);
    const source = sec.id === 'archive' ? archived : inSection(inbox, sec);
    const list = source.filter((t) => matches(t, query));
    const requests = sec.id === 'archive' ? [] : list.filter((t) => t.incomingRequest);
    const rest = list.filter((t) => !requests.includes(t));
    fill(root,
        ...requests.map((t) => createActionBanner({
            title: `Yarn request from ${t.name}`, text: `“${t.lastBody || ''}” · Accept to reply. Declining archives it and they cannot send more.`,
            actions: [
                { label: 'Accept', variant: 'ok', onClick: () => respond(t, true) },
                { label: 'Decline', variant: 'danger', onClick: () => respond(t, false) },
            ],
        }).element),
        rest.length ? h('section', { class: 'yn-group' },
            h('h2', { class: 'sp-h2', text: sec.id === 'all' ? 'Recent yarns' : sec.label }),
            h('div', { class: 'yn-list' }, ...rest.map((t) => row(t, archiveQuick(t))))) : null,
        list.length ? null : h('p', { class: 'yn-empty', text: query ? 'No yarns match that search.' : sec.id === 'archive' ? 'Nothing archived.' : sec.id === 'wespace' ? 'The collaborators room opens here once someone accepts your request.' : sec.id === 'workspace' ? 'A Workspace opens here when a space forms and you are in it.' : 'No yarns here yet. Tap + to start one.' }));
}

function renderBlocked(root) {
    const shown = blocked.filter((b) => b.username.toLowerCase().includes(query.trim().toLowerCase()));
    root.replaceChildren(
        h('section', { class: 'yn-group' },
            h('h2', { class: 'sp-h2', text: 'Blocked' }),
            h('p', { class: 'sp-sub', text: 'They are not told. Neither of you can send new MySpace yarns. Shared WeSpaces and Workspaces are unaffected.' }),
            shown.length
                ? h('div', { class: 'yn-list' }, ...shown.map((b) => h('div', { class: 'yn-item' },
                    h('div', { class: 'yn-row yn-row--static' }, face(b.username, 'sp-avatar--sm'),
                        h('span', { class: 'yn-body' }, h('strong', { text: b.username }), h('span', { class: 'yn-last', text: (ago(b.since) === 'now' ? 'Blocked just now' : `Blocked ${ago(b.since)} ago`) }))),
                    h('button', { class: 'yn-quick', type: 'button', text: 'Unblock', onclick: () => act(() => yarnUnblock(b.userId), `Unblocked ${b.username}.`) }))))
                : h('p', { class: 'yn-empty', text: blocked.length ? 'No one blocked matches that search.' : 'You have not blocked anyone.' })));
}

async function respond(t, accept) {
    try {
        await yarnRespond(t.id, accept);
        await loadAll();
        toast(accept ? 'Accepted. You can reply now.' : 'Declined and archived.');
        if (accept) { if (location.hash === '#t/' + t.id) route(); else location.hash = '#t/' + t.id; }
        else go('#' + lastSection);
    } catch (err) { toast(err.message); }
}

// ---- one thread ------------------------------------------------------------
function renderThread() {
    const t = openThread;
    const actions = h('div', { class: 'yn-actions' });
    const paintActions = () => fill(actions,
        h('button', { class: 'sp-btn', type: 'button', text: t.pinned ? 'Unpin' : 'Pin', onclick: () => tweak({ pinned: !t.pinned }) }),
        h('button', { class: 'sp-btn', type: 'button', text: t.muted ? 'Unmute' : 'Mute', onclick: () => tweak({ muted: !t.muted }) }),
        h('button', { class: 'sp-btn', type: 'button', text: t.archived ? 'Restore' : 'Archive', onclick: () => tweak({ archived: !t.archived }, true) }),
        h('button', { class: 'sp-btn', type: 'button', text: 'Report', onclick: report }),
        t.otherUserId ? h('button', { class: 'sp-btn sp-btn--danger', type: 'button', text: 'Block', onclick: block }) : null);

    async function tweak(prefs, leave) {
        try {
            Object.assign(t, await yarnPrefs(t.id, prefs));
            if (leave) { await loadAll(); toast(t.archived ? 'Archived. A new yarn brings it back.' : 'Restored to your inbox.'); return go('#' + lastSection); }
            paintActions();
        } catch (err) { toast(err.message); }
    }
    // Any tier can be reported. The admin sends a moderator, and everyone in the Yarnspace is told one was introduced.
    async function report() {
        const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Report this Yarnspace', html: '<h3 class="glass-blur-dialog__title">Report this Yarnspace</h3><form class="sp-form"></form>' });
        const box = h('textarea', { class: 'sp-input', rows: 5, maxlength: 1000, required: true, placeholder: 'What is wrong? Say what happened and who was involved.' });
        const err = h('p', { class: 'yn-error', role: 'alert', hidden: true });
        const form = panel.querySelector('.sp-form');
        form.append(box, err,
            h('p', { class: 'yn-hint', text: 'The platform admin reviews it and may assign a moderator, who can read this Yarnspace (and nothing else of yours) while it is looked at. Everyone in it is told a moderator was introduced, not who reported.' }),
            h('div', { class: 'glass-blur-dialog__actions' },
                h('button', { class: 'glass-blur-dialog__btn glass-blur-dialog__btn--ghost', type: 'button', onclick: close }, 'Cancel'),
                h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Send report')));
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            err.hidden = true;
            try { await yarnReport(t.id, box.value); close(); toast('Report sent. The admin will look at it.'); }
            catch (ex) { err.textContent = ex.message; err.hidden = false; }
        });
    }
    async function block() {
        if (!(await glassBlurConfirm(`Block ${t.name}? They are not told. Neither of you can send new MySpace yarns until you unblock them in Blocked.`, { title: 'Block this person?', okText: 'Block', danger: true }))) return;
        try { await yarnBlock(t.otherUserId); await loadAll(); toast(`Blocked ${t.name}.`); go('#' + lastSection); }
        catch (err) { toast(err.message); }
    }

    const reason = t.status === 'DECLINED' ? 'This yarn request was declined.' : t.incomingRequest ? 'Accept the request to reply.' : '';
    paintActions();
    threadView = createYarnThread({
        meId: me.id, showNames: t.tier !== 'MYSPACE', disabledReason: reason,
        load: (before) => yarnHistory(t.id, before),
        send: (body) => yarnSend(t.id, body),
        onRead: () => yarnMarkRead(t.id).catch(() => {}),
        // The server says when this room changes (a yarn arrived, or ticks moved); the thread refetches itself, silently.
        signals: (refresh) => {
            const same = (s) => { if (s.thread === t.id) refresh(); };
            const offs = [live.on('yarn', same), live.on('receipt', same), live.onResync(refresh)];
            return () => offs.forEach((off) => off());
        },
        isLive: () => live.connected,
    });
    fill(document.getElementById('section'),
        actions,
        t.incomingRequest ? createActionBanner({
            title: `${t.name} wants to yarn you`, text: 'Accept to reply. Declining archives it and they cannot send more.',
            actions: [{ label: 'Accept', variant: 'ok', onClick: () => respond(t, true) }, { label: 'Decline', variant: 'danger', onClick: () => respond(t, false) }],
        }).element : null,
        threadView.element);
}

// ---- dialogs ---------------------------------------------------------------
// A new yarn is always one person: WeSpaces and Workspaces open by themselves from posts and spaces.
async function openNew() {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'New yarn', html: '<h3 class="glass-blur-dialog__title">New yarn</h3><form class="sp-form"></form>' });
    const who = h('input', { class: 'sp-input', id: 'new-who', list: 'yn-people', autocomplete: 'off', placeholder: 'username', required: true });
    const people = h('datalist', { id: 'yn-people' });
    const first = h('textarea', { class: 'sp-input', id: 'new-body', rows: '3', maxlength: '2000', placeholder: 'Your first yarn', required: true });
    const err = h('p', { class: 'yn-error', role: 'alert', hidden: true });
    let timer;
    who.addEventListener('input', () => {   // suggest people as they type
        clearTimeout(timer);
        const q = who.value.trim();
        timer = setTimeout(async () => {
            const found = q.length >= 2 ? await yarnDirectory(q).catch(() => []) : [];
            people.replaceChildren(...found.map((p) => h('option', { value: p.username })));
        }, 250);
    });
    const form = panel.querySelector('.sp-form');
    form.append(h('label', { for: 'new-who' }, 'Who?', who, people), h('label', { for: 'new-body' }, 'First yarn', first), err,
        h('p', { class: 'yn-hint', text: 'Group rooms open on their own: a WeSpace when someone becomes your collaborator, a Workspace when a space forms.' }),
        h('div', { class: 'glass-blur-dialog__actions' },
            h('button', { class: 'glass-blur-dialog__btn glass-blur-dialog__btn--ghost', type: 'button', onclick: close }, 'Cancel'),
            h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Start')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        err.hidden = true;
        try {
            const t = await yarnStartMySpace(who.value.trim(), first.value);
            close();
            await loadAll();
            location.hash = '#t/' + t.id;
        } catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

async function openSettings() {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Yarns settings', html: '<h3 class="glass-blur-dialog__title">Yarns settings</h3><form class="sp-form"></form>' });
    const pick = h('select', { class: 'sp-input', id: 'default-chat' }, ...SECTIONS.map((s) => h('option', { value: s.id, text: s.label, selected: s.id === (savedDefault() || 'all') })));
    panel.querySelector('.sp-form').append(
        h('label', { for: 'default-chat' }, 'Default chat', pick),
        h('p', { class: 'yn-hint', text: 'What opens first when you tap Yarns from the Dash.' }),
        h('p', { class: 'yn-hint' }, `Signed in as ${me.username}. `, h('a', { href: '/HTML-pages/profile.html' }, 'My profile')),
        h('div', { class: 'glass-blur-dialog__actions' },
            h('button', { class: 'glass-blur-dialog__btn glass-blur-dialog__btn--ghost', type: 'button', onclick: async () => { await logoutUser().catch(() => {}); toLogin(); } }, 'Sign out'),
            h('button', { class: 'glass-blur-dialog__btn', type: 'button', onclick: () => {
                try { localStorage.setItem(PREF_KEY, pick.value); } catch { /* private mode: setting just won't stick */ }
                toast('Default chat saved.'); close();
            } }, 'Save')));
}

// ---- boot ------------------------------------------------------------------
async function start() {
    try {
        me = await yarnMe();
        if (!me) return toLogin();
        await loadAll();
    } catch (err) { me = null; return fatal(err); }
    route();
}

async function boot() {
    preloadGlassBlurDialog(); preloadYarnThread();
    await preloadActionBanner();
    await mountThemeSwitcher('#theme-slot', { inline: true });
    nav = await mountNavSelector('#nav', {
        placement: 'bottom', links: SECTIONS.map((s) => s.label), hrefs: SECTIONS.map((s) => '#' + s.id), icons: SECTIONS.map((s) => svg(ICON[s.id])),
        activeIndex: SECTIONS.indexOf(currentSection()), collapseWhenIdle: true, idleMs: 5000,
        onChange: (_l, href) => go(href.slice(href.lastIndexOf('#'))),   // href can arrive absolute
    });
    document.getElementById('settings-btn').addEventListener('click', () => me && openSettings());
    document.getElementById('new-btn').addEventListener('click', () => me && openNew());
    document.getElementById('search').addEventListener('input', (e) => { query = e.target.value; if (!openThread && me) renderSection(); });
    document.getElementById('header-back').addEventListener('click', (e) => { if (openThread) { e.preventDefault(); go('#' + lastSection); } });
    window.addEventListener('hashchange', () => { if (me) route(); });
    // The lists (unread counts, last yarn, new requests) refresh when the server says a yarn arrived, and again after a reconnect.
    // The timer is only the net underneath: every 15s with no live socket, every 60s with one.
    let listTimer = null;
    const refreshLists = () => {
        clearTimeout(listTimer);
        listTimer = setTimeout(() => {
            if (!me) return;
            loadAll().then(() => { if (!openThread) { setHeader(null); renderSection(); } }).catch(() => {});
        }, 250);   // a burst of signals becomes one refresh
    };
    live.on('yarn', refreshLists); live.on('receipt', refreshLists); live.onResync(refreshLists);
    let lastTick = 0;
    setInterval(() => {
        const every = live.connected ? 60_000 : 15_000;
        if (me && !openThread && document.visibilityState === 'visible' && Date.now() - lastTick >= every) { lastTick = Date.now(); refreshLists(); }
    }, 5000);
    start();
}
boot();
