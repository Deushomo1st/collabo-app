// Every notification, on a page of its own: tabs by kind, a search box, and the funnel for unread-only and read-all.
import '/js/services/live.js';
import { live } from '/js/services/live.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { currentUser, notificationsList, notificationRead, notificationsReadAll } from '/js/services/api.js';
import { h, toast } from '/js/services/dom.js';
import { openMenu, toLogin } from '/js/services/review-ui.js';
import { feedTabs } from '/js/services/feed-tabs.js';
import { face } from '/js/services/face.js';
import { whoIn } from '/js/services/notice-who.js';
import { skeletonRows } from '/js/services/skeleton.js';

const $list = document.getElementById('list'), $sum = document.getElementById('summary'), $filter = document.getElementById('filter');
const TABS = [['ALL', 'All'], ['SPACES', 'Spaces'], ['ACTIVITY', 'Activity'], ['PERSONAL', 'Personal']];
const STEP = 20;
let items = [], tab = 'ALL', unreadOnly = false, query = '', limit = STEP;

const search = h('input', { class: 'rv-search sp-input', type: 'search', placeholder: 'Search notifications', autocomplete: 'off', 'aria-label': 'Search notifications' });
search.addEventListener('input', () => { query = search.value.trim().toLowerCase(); limit = STEP; draw(); });
$sum.replaceChildren(search);
$sum.style.display = 'block';

const inTab = (n, k) => k === 'ALL' || n.actionRequired || n.bucket === k;

function draw() {
    const shown = items.filter((n) => inTab(n, tab) && (!unreadOnly || !n.read) && (!query || `${n.title} ${n.body || ''}`.toLowerCase().includes(query)));
    const page = shown.slice(0, limit);
    $list.replaceChildren(...(page.length ? page.map(row) : [h('p', { class: 'rv-empty', text: items.length ? 'No matches.' : 'No notifications yet.' })]),
        ...(shown.length > limit ? [h('button', { class: 'pc-btn', type: 'button', text: `Show ${Math.min(STEP, shown.length - limit)} more`, onclick: () => { limit += STEP; draw(); } })] : []));
}

const row = (n) => h('button', { class: `rv-note${n.read ? '' : ' is-unread'}`, type: 'button', onclick: () => {
    if (!n.read) { n.read = true; notificationRead(n.id).catch(() => {}); }
    if (n.link) location.href = n.link; else draw();
} }, whoIn(n).length > 0 && h('div', { class: 'rv-note__who' }, ...whoIn(n).map((u) => face(u, 'sp-avatar--sm'))), h('b', { text: n.title }), n.body && h('span', { text: n.body }), n.createdAt && h('time', { datetime: n.createdAt, text: new Date(n.createdAt).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }) }));

async function load() { items = (await notificationsList()) || []; draw(); }

$filter.addEventListener('click', () => openMenu($filter,
    [[{ key: 'all', label: 'Everything' }, { key: 'unread', label: 'Unread only' }], [{ key: 'readall', label: 'Mark all as read' }]],
    (k) => (k === 'unread' ? unreadOnly : k === 'all' ? !unreadOnly : false),
    async (k) => {
        if (k === 'readall') { try { await notificationsReadAll(); items.forEach((n) => { n.read = true; }); toast('All read.'); } catch (err) { toast(err.message); } } else unreadOnly = k === 'unread';
        draw();
    }));
// The Gaze's switcher: the kinds as tabs in the header, a swipe sideways anywhere moves to the next one.
feedTabs(document.getElementById('filter-nav'), { labels: TABS.map(([, label]) => label), at: () => TABS.findIndex(([k]) => k === tab), go: (i) => { tab = TABS[i][0]; limit = STEP; draw(); } });
const top = document.getElementById('nt-top'), mark = () => top.classList.toggle('is-scrolled', scrollY > 8);
addEventListener('scroll', mark, { passive: true }); mark();

$list.replaceChildren(...skeletonRows(5));   // the list's shape while it loads
(async () => {
    try {
        const me = await currentUser();
        if (!me) return toLogin();
        mountMainNav(me.username, { onGaze: () => { location.href = '/HTML-pages/gaze.html'; } });
        await load();
        live.on('notification', () => load().catch(() => {}));
    } catch (err) {
        if (err.status === 401) return toLogin();
        $list.replaceChildren(h('p', { class: 'rv-empty', text: err.message || 'Could not load notifications.' }));
    }
})();
