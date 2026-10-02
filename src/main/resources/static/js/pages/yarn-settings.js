// Yarns settings, a page of its own: default chat, who can message you, theme, the Archive and Blocked lists, sign out.
// Text goes in through textContent only (h() never sets innerHTML for user text).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import '/js/services/nav-mode.js';
import { fanActions } from '/js/services/fan-actions.js';
import { GEAR } from '/js/services/icons.js';
import { h } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { messagePrivacyRow } from '/js/services/message-privacy.js';
import { currentUser, logoutUser, yarnThreads, yarnPrefs, yarnBlocked, yarnUnblock } from '/js/services/api.js';
import { SECTIONS, TIER_LABEL, ago } from '/js/pages/yarnspaces-data.js';

const PREF_KEY = 'collaboYarnDefault';   // which section opens when Yarns is entered (read by yarnspaces.js)
const svg = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const ICONS = [
    '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/>',
    GEAR,
];
const root = () => document.getElementById('section');
function toast(text) {
    const el = document.getElementById('toast');
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2600);
}
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));

let archived = [], blocked = [];
const load = async () => { [archived, blocked] = await Promise.all([yarnThreads('archived'), yarnBlocked()]); };

async function change(action, done) {
    try { await action(); await load(); drawLists(); toast(done); } catch (err) { toast(err.message); }
}

function archiveRows() {
    if (!archived.length) return [h('p', { class: 'yn-empty', text: 'Nothing archived.' })];
    return archived.map((t) => h('div', { class: 'yn-item' },
        h('a', { class: 'yn-row', href: `/HTML-pages/yarnspaces.html#t/${t.id}` },
            h('span', { class: 'yn-body' },
                h('span', { class: 'yn-line' }, h('strong', { text: t.name }), h('span', { class: 'sp-tag', text: TIER_LABEL[t.tier] }), h('time', { text: ago(t.lastAt) })),
                h('span', { class: 'yn-last', text: `${t.lastSender}: ${t.lastBody || ''}` }))),
        h('button', { class: 'yn-quick', type: 'button', text: 'Restore', onclick: () => change(() => yarnPrefs(t.id, { archived: false }), 'Restored to your inbox.') })));
}

function blockedRows() {
    if (!blocked.length) return [h('p', { class: 'yn-empty', text: 'You have not blocked anyone.' })];
    return blocked.map((b) => h('div', { class: 'yn-item' },
        h('div', { class: 'yn-row yn-row--static' }, face(b.username, 'sp-avatar--sm'),
            h('span', { class: 'yn-body' }, h('strong', { text: b.username }), h('span', { class: 'yn-last', text: ago(b.since) === 'now' ? 'Blocked just now' : `Blocked ${ago(b.since)} ago` }))),
        h('button', { class: 'yn-quick', type: 'button', text: 'Unblock', onclick: () => change(() => yarnUnblock(b.userId), `Unblocked ${b.username}.`) })));
}

function drawLists() {
    document.getElementById('yn-archive').replaceChildren(...archiveRows());
    document.getElementById('yn-blocked').replaceChildren(...blockedRows());
}

async function render(me) {
    const pick = h('select', { class: 'sp-input', id: 'default-chat' }, ...SECTIONS.map((s) => h('option', { value: s.id, text: s.label })));
    try { pick.value = localStorage.getItem(PREF_KEY) || SECTIONS[0].id; } catch { /* private mode: the first section */ }
    if (!pick.value) pick.value = SECTIONS[0].id;
    pick.addEventListener('change', () => {
        try { localStorage.setItem(PREF_KEY, pick.value); toast('Default chat saved.'); } catch { toast('Could not save in this browser.'); }
    });
    root().replaceChildren(
        h('section', { class: 'yn-group sp-glass yn-card sp-form' },
            h('label', { for: 'default-chat' }, 'Default chat', pick),
            h('p', { class: 'yn-hint', text: 'What opens first when you tap Yarns.' }),
            await messagePrivacyRow(me.username, toast),
            h('p', { class: 'yn-hint', text: 'Only new conversations are limited. Chats you already have carry on.' })),
        h('section', { class: 'yn-group' }, h('h2', { class: 'sp-h2', text: 'Archive' }), h('div', { class: 'yn-list', id: 'yn-archive' })),
        h('section', { class: 'yn-group' }, h('h2', { class: 'sp-h2', text: 'Blocked' }),
            h('p', { class: 'yn-hint', text: 'They are not told. Neither of you can send new MySpace yarns. Shared WeSpaces and WorkSpaces are unaffected.' }),
            h('div', { class: 'yn-list', id: 'yn-blocked' })),
        h('p', { class: 'yn-hint' }, `Signed in as ${me.username}. `, h('a', { href: '/HTML-pages/profile.html', text: 'My profile' })),
        h('button', { class: 'yn-quick yn-signout', type: 'button', text: 'Sign out', onclick: async () => { await logoutUser().catch(() => {}); toLogin(); } }));
    drawLists();
}

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    // the same bar as the Yarns page with Settings chosen; the others go back to that list
    await mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 0, holdActions: fanActions(), activeIndex: SECTIONS.length,
        links: [...SECTIONS.map((s) => s.label), 'Settings'], hrefs: [...SECTIONS.map((s) => `/HTML-pages/yarnspaces.html#${s.id}`), '#'],
        icons: ICONS.map(svg),
        onChange: (_l, href) => { if (href.includes('yarnspaces.html')) location.href = href.slice(href.indexOf('/HTML-pages')); },
    });
    try { await load(); } catch (err) { if (err.status === 401) return toLogin(); toast(err.message); }
    await render(me);
}
boot();
