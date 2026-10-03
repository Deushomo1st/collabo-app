// Everything you applied to, on a page of its own. Tabs by where each application stands; an accepted one leads to the space.
import '/js/services/live.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { currentUser, applicationsMine, applicationWithdraw } from '/js/services/api.js';
import { h, toast, day, profileHref } from '/js/services/dom.js';
import { drawTabs, toLogin } from '/js/services/review-ui.js';
import { skeletonCards } from '/js/services/skeleton.js';

const $tabs = document.getElementById('tabs'), $list = document.getElementById('list');
const TABS = [['OPEN', 'Pending', ['SUBMITTED']], ['SHORTLISTED', 'Shortlisted', ['SHORTLISTED']], ['ACCEPTED', 'Accepted', ['ACCEPTED']], ['DECLINED', 'Closed', ['DECLINED', 'WITHDRAWN']]];
let rows = [], tab = 'OPEN';

function draw() {
    const of = (t) => rows.filter((r) => t[2].includes(r.state));
    drawTabs($tabs, TABS.map((t) => [t[0], t[1], of(t).length]), tab, (k) => { tab = k; draw(); });
    const shown = of(TABS.find((t) => t[0] === tab));
    $list.replaceChildren(...(shown.length ? shown.map(card) : [h('p', { class: 'rv-empty', text: rows.length ? 'Nothing here.' : 'You have not applied to anything yet.' })]));
}

function next(a) {
    if (a.state === 'ACCEPTED') {
        return a.postStatus === 'formed'
            ? h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/space.html?post=${a.postId}`, text: 'Join the space' })
            : h('span', { class: 'pc-hint', text: 'Accepted. Waiting for the founder to form the space.' });
    }
    if (a.state === 'SUBMITTED' || a.state === 'SHORTLISTED') {
        return h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Withdraw', onclick: async () => {
            try { await applicationWithdraw(a.id); rows = rows.map((r) => (r.id === a.id ? { ...r, state: 'WITHDRAWN' } : r)); draw(); } catch (err) { toast(err.message); }
        } });
    }
    return null;
}

const card = (a) => h('div', { class: 'rv-card rv-card--post' },
    h('div', { class: 'rv-head' },
        h('a', { class: 'rv-title', href: `/HTML-pages/view-post.html?id=${a.postId}`, text: a.postTitle }),
        h('time', { class: 'rv-time', datetime: a.createdAt, text: day(a.createdAt) })),
    h('span', { class: 'rv-at' }, 'by ', h('a', { class: 'rv-name', href: profileHref(a.postAuthor.username), text: a.postAuthor.username }),
        a.postStatus === 'closed' ? ' · applications closed' : '', a.state === 'WITHDRAWN' ? ' · withdrawn' : ''),
    h('p', { class: 'rv-text', text: a.statement }),
    h('div', { class: 'rv-acts' }, next(a), h('a', { class: 'pc-btn', href: `/HTML-pages/view-post.html?id=${a.postId}`, text: 'View post' })));

document.getElementById('back').addEventListener('click', (e) => { if (history.length > 1 && document.referrer.startsWith(location.origin)) { e.preventDefault(); history.back(); } });
$list.replaceChildren(...skeletonCards(3));   // the cards' shape while they load
(async () => {
    try {
        const me = await currentUser();
        if (!me) return toLogin();
        mountMainNav(me.username);
        rows = await applicationsMine();
        tab = TABS.find((t) => rows.some((r) => t[2].includes(r.state)))?.[0] || tab;   // open on the first tab that has something
        draw();
    } catch (err) {
        if (err.status === 401) return toLogin();
        $list.replaceChildren(h('p', { class: 'rv-empty', text: err.message || 'Could not load your applications.' }));
    }
})();
