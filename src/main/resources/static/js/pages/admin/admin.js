// Admin console shell: the key gate, the sidebar, the status strip that stays on every view, and hash routing (#users, #database ...).
// Views live next to this file and return a DOM node. An AdminAuthError anywhere sends you back to the gate.
import * as api from '/js/services/admin-api.js';
import { h } from '/js/services/dom.js';
import '/js/components/password-toggle.js';
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { overview, checks } from './overview.js';
import { usersView } from './users.js';
import { moderatorsView } from './moderators.js';
import { investigationsView } from './investigations.js';
import { reportsView } from './reports.js';
import { databaseView } from './database.js';
import { helpView } from './help.js';

let latest = null, latestAt = 0;   // the last status answer, shared by the strip and the overview

const VIEWS = {
    overview: { label: 'Overview', build: async () => overview(latest) },
    investigations: { label: 'Investigations', build: investigationsView },
    reports: { label: 'Reports', build: reportsView },
    users: { label: 'Users', build: usersView },
    moderators: { label: 'Moderators', build: moderatorsView },
    database: { label: 'Database', build: databaseView },
    help: { label: 'Help', build: async () => helpView() },
};

const $ = (id) => document.getElementById(id);
const ICON = { ok: '●', warn: '▲', bad: '✕' };

function showGate(message = '') {
    $('app').hidden = true;
    $('gate').hidden = false;
    $('gate-error').textContent = message;
    $('gate-key').value = '';
    $('gate-key').focus();
}

function showApp() {
    $('gate').hidden = true;
    $('app').hidden = false;
    route();
}

async function refreshStatus() {
    latest = await api.status();
    latestAt = Date.now();
    $('strip').replaceChildren(...checks(latest).map((c) => h('a', { class: `ad-pill is-${c.state}`, href: '#overview', title: c.fix || c.value },
        h('span', { 'aria-hidden': 'true', text: ICON[c.state] }), ` ${c.label}: ${c.value}`)));
}

const current = () => (location.hash.slice(1) in VIEWS ? location.hash.slice(1) : 'overview');

async function route() {
    const key = current();
    nav?.setActive(Object.keys(VIEWS).indexOf(key));
    const view = $('view');
    view.replaceChildren(h('p', { class: 'ad-empty', text: 'Loading…' }));
    try {
        if (Date.now() - latestAt > 3000) await refreshStatus();   // the strip is always current; boot has just fetched it
        const node = await VIEWS[key].build();
        if (current() === key) view.replaceChildren(node);   // the user may have moved on while this loaded
    } catch (e) {
        if (e instanceof api.AdminAuthError) { api.setKey(''); return showGate(e.message); }
        view.replaceChildren(h('div', { class: 'ad-view' }, h('p', { class: 'ad-empty', text: e.message }), h('button', { class: 'ad-btn', type: 'button', text: 'Try again', onclick: route })));
    }
}

async function unlock() {
    const key = $('gate-key').value.trim();
    if (!key) return void ($('gate-error').textContent = 'Enter the admin key.');
    api.setKey(key);
    try { await refreshStatus(); showApp(); }
    catch (e) { api.setKey(''); showGate(e instanceof api.AdminAuthError ? e.message : `Could not reach the server (${e.message}).`); }
}

function signOut() { api.setKey(''); showGate(); }

let nav;   // the pill nav; route() keeps its bubble on the current view
mountNavSelector('#nav', { links: [...Object.values(VIEWS).map((v) => v.label), 'Sign out'], hrefs: [...Object.keys(VIEWS).map((k) => `#${k}`), '#signout'], activeIndex: Object.keys(VIEWS).indexOf(current()),
    onChange: (_l, href) => { const to = href.slice(href.lastIndexOf('#')); if (to === '#signout') signOut(); else location.hash = to; } }).then((n) => { nav = n; nav?.setActive(Object.keys(VIEWS).indexOf(current())); });
$('gate-form').addEventListener('submit', (e) => { e.preventDefault(); unlock(); });
window.addEventListener('hashchange', () => { if (!$('app').hidden) route(); });
// A view that hits a wrong key from inside a click handler lands here.
window.addEventListener('unhandledrejection', (e) => {
    if (e.reason instanceof api.AdminAuthError) { e.preventDefault(); api.setKey(''); showGate(e.reason.message); }
});
setInterval(() => { if (!$('app').hidden && !document.hidden) refreshStatus().catch(() => {}); }, 60000);

if (api.getKey()) refreshStatus().then(showApp).catch((e) => { api.setKey(''); showGate(e instanceof api.AdminAuthError ? e.message : ''); });
else showGate();

