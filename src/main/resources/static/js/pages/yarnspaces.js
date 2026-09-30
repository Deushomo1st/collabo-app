// Yarnspaces hub: MySpace (1-on-1), WeSpace (1-to-many) and Workspaces, reached through the "Yarns" label.
// Text goes in through textContent only (h() never sets innerHTML).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { createActionBanner, preloadActionBanner } from '/js/components/action-banner/action-banner.js';
import { TIERS, initialThreads, inTier, actionThreads, unreadTotal, markRead, search, ago } from '/js/pages/yarnspaces-data.js';

let threads = initialThreads();
let query = '';

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
};
const SECTIONS = TIERS.map((t) => ({ ...t, icon: svg(ICON[t.id]) }));
const currentTier = () => (SECTIONS.find((s) => location.hash === '#' + s.id) || SECTIONS[0]).id;
let nav;

function toast(text) {
    const el = document.getElementById('toast');
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2200);
}

const face = ([name, hue], cls = '') => h('span', { class: `sp-avatar ${cls}`, style: `--hue:${hue}`, text: name[0].toUpperCase() });

function open(t) {
    if (t.href) { location.href = t.href; return; }
    threads = markRead(threads, t.id);
    render();
    toast(`${t.name}: the ${t.tier === 'myspace' ? 'MySpace' : 'WeSpace'} yarn view is not built yet.`);
}

function row(t) {
    return h('button', { class: `yn-row ${t.unread ? 'is-unread' : ''}`, onclick: () => open(t) },
        h('span', { class: 'yn-faces' }, ...t.people.slice(0, 3).map((p) => face(p, 'sp-avatar--sm'))),
        h('span', { class: 'yn-body' },
            h('span', { class: 'yn-line' },
                h('strong', { text: t.name }),
                h('span', { class: 'sp-tag', text: t.tag }),
                h('time', { text: ago(t.mins) })),
            h('span', { class: 'yn-last', text: `${t.who}: ${t.last}` })),
        t.unread ? h('span', { class: 'yn-badge', 'aria-label': `${t.unread} unread yarns`, text: String(t.unread) }) : null);
}

function group(title, list) {
    return list.length ? h('section', { class: 'yn-group' }, h('h2', { class: 'sp-h2', text: title }), h('div', { class: 'yn-list' }, ...list.map(row))) : null;
}

function render() {
    const tier = currentTier();
    const scoped = search(inTier(threads, tier), query);
    const rest = scoped.filter((t) => !t.action);
    document.getElementById('section').replaceChildren(...[
        ...actionThreads(scoped).map((t) => createActionBanner({
            title: t.action.title, text: `${t.name} · ${t.action.text}`,
            actions: [{ label: 'Open', variant: 'ok', onClick: () => open(t) }],
        }).element),
        group(tier === 'all' ? 'Recent yarns' : TIERS.find((x) => x.id === tier).label, rest),
        scoped.length ? null : h('p', { class: 'yn-empty', text: query ? 'No yarns match that search.' : 'No yarns here yet.' }),
    ].filter(Boolean));
    const n = unreadTotal(threads);
    document.getElementById('header-sub').textContent = n ? `${n} unread ${n === 1 ? 'yarn' : 'yarns'}` : 'All caught up';
}

async function boot() {
    await preloadActionBanner();
    render();
    await mountThemeSwitcher('#theme-slot', { inline: true });
    nav = await mountNavSelector('#nav', {
        placement: 'bottom', links: SECTIONS.map((s) => s.label), hrefs: SECTIONS.map((s) => '#' + s.id), icons: SECTIONS.map((s) => s.icon),
        activeIndex: SECTIONS.findIndex((s) => s.id === currentTier()), collapseWhenIdle: true, idleMs: 5000,
        onChange: (_l, href) => {   // href can arrive absolute
            const hash = href.slice(href.lastIndexOf('#'));
            if (location.hash === hash) render(); else location.hash = hash;
        },
    });
    document.getElementById('search').addEventListener('input', (e) => { query = e.target.value; render(); });
    window.addEventListener('hashchange', () => { nav.setActive(SECTIONS.findIndex((s) => s.id === currentTier())); render(); });
}
boot();
