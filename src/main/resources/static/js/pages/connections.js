// Connections page: a profile's followers, following and MyGuy (people who follow each other), TikTok style.
// Reached as connections.html?u=<username>&tab=followers|following|myguy. Search matches letters in any order.
import '/js/services/live.js';
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { currentUser, connections } from '/js/services/api.js';
import { face } from '/js/services/face.js';
import { h } from '/js/services/dom.js';
import { skeletonRows } from '/js/services/skeleton.js';

const TABS = [['following', 'Following'], ['followers', 'Followers'], ['myguy', 'MyGuy']];
const params = new URLSearchParams(location.search);
let data = null, mine = false, tab = TABS.some(([k]) => k === params.get('tab')) ? params.get('tab') : 'following';

const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
const letters = (s) => { const m = {}; for (const c of s.toLowerCase().replace(/[^\p{L}\p{N}]/gu, '')) m[c] = (m[c] || 0) + 1; return m; };
/** True when every letter of the query is in the name, in any order (a letter typed twice must appear twice). */
export const hasLetters = (name, query) => { const have = letters(name), want = letters(query); return Object.keys(want).every((c) => (have[c] || 0) >= want[c]); };

const rows = () => data[tab === 'myguy' ? 'myGuy' : tab];

function drawTabs() {
    $tabs.replaceChildren(...TABS.filter(([k]) => k !== 'myguy' || mine).map(([k, label]) => h('button', {
        class: 'cn-tab' + (k === tab ? ' is-on' : ''), type: 'button', role: 'tab', 'aria-selected': String(k === tab),
        onclick: () => { tab = k; history.replaceState(null, '', `?u=${encodeURIComponent(who)}&tab=${k}`); drawTabs(); drawList(); },
    }, h('strong', { text: String(data[k === 'myguy' ? 'myGuy' : k].length) }), h('span', { text: label }))));
}

function drawList() {
    const q = $q.value.trim();
    const shown = rows().filter((p) => !q || hasLetters(p.username, q));
    $list.replaceChildren(...(shown.length ? shown.map((p) => h('li', {}, h('a', { class: 'cn-row', href: `/HTML-pages/profile.html?u=${encodeURIComponent(p.username)}` },
        face(p.username, 'cn-face'),
        h('span', { class: 'cn-who' }, h('strong', { text: p.username }), p.preferredTitle && h('small', { text: p.preferredTitle })),
        h('span', { class: 'cn-go', text: 'View' }))))
        : [h('li', { class: 'cn-empty', text: q ? 'No one has those letters.' : 'No one yet.' })]));
}

const $tabs = document.getElementById('tabs'), $q = document.getElementById('q'), $list = document.getElementById('list');
let who = params.get('u');
(async () => {
    $list.replaceChildren(...skeletonRows(5).map((r) => h('li', {}, r)));
    try {
        const me = await currentUser();
        if (!me) return toLogin();
        who = who || me.username;
        document.getElementById('header-title').textContent = who;
        mountMainNav(me.username, 'Profile');
        mine = who.toLowerCase() === me.username.toLowerCase();
        if (!mine && tab === 'myguy') tab = 'following';   // MyGuy is for the owner's eyes only
        data = await connections(who);
        drawTabs(); drawList();
        $q.addEventListener('input', drawList);
    } catch (err) {
        if (err.status === 401) return toLogin();
        $list.replaceChildren(h('li', { class: 'cn-empty', text: err.status === 404 ? 'No one has that username.' : (err.message || 'Could not load.') }));
    }
})();
mountThemeSwitcher('#theme-slot', { inline: true, collapse: true });
