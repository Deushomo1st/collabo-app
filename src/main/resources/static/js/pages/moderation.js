// Moderator screen: the queue of open appeals, and for each one the room's yarns around the removal (nothing else)
// with a binary decision. Only moderators and admins get past the server; everyone else sees a plain note.
// All network calls live in js/services/api.js; text goes in through textContent only.
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { currentUser, appealQueue, appealDetail, appealDecide } from '/js/services/api.js';
import { h, toast, day, profileHref } from '/js/services/dom.js';

const section = document.getElementById('section');
const say = (text) => section.replaceChildren(h('p', { class: 'gz-empty', text }));
const time = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

async function decide(id, outcome) {
    try { await appealDecide(id, outcome); toast(outcome === 'DROPS' ? 'Badge dropped.' : 'Badge stays.'); await showQueue(); }
    catch (err) { toast(err.message); }
}

async function showAppeal(id) {
    let d;
    try { d = await appealDetail(id); } catch (err) { return say(err.message); }
    const r = d.record;
    section.replaceChildren(
        h('button', { class: 'pf-btn', type: 'button', text: '← Queue', onclick: showQueue }),
        h('article', { class: 'pf-entry sp-glass' },
            h('div', { class: 'pf-entry__top' }, h('strong', { text: r.spaceName }), h('time', { datetime: r.createdAt, text: day(r.createdAt) })),
            h('p', {}, h('a', { href: profileHref(r.removedBy.username), text: r.removedBy.username }), ' removed ',
                h('a', { href: profileHref(r.removed.username), text: r.removed.username }), `: ${r.reason}`),
            h('p', { class: 'mod-note' }, h('strong', { text: `${r.removed.username}'s appeal: ` }), d.note)),
        h('h2', { class: 'mod-h', text: 'The room, a day before to an hour after' }),
        d.history.length === 0 ? h('p', { class: 'gz-empty', text: 'Nothing was said in that window.' })
            : h('div', { class: 'mod-history sp-glass' }, ...d.history.map((l) => h('div', { class: `mod-line${l.system ? ' is-system' : ''}` },
                h('small', { text: `${time(l.createdAt)} · ${l.system ? 'system' : l.sender}` }), h('p', { text: l.body })))),
        h('p', { class: 'pf-hint', text: 'The removal stands either way. Dropping only takes the badge off the record.' }),
        h('div', { class: 'pf-actions' },
            h('button', { class: 'pf-btn', type: 'button', text: 'Badge stays', onclick: () => decide(id, 'STICKS') }),
            h('button', { class: 'pf-btn pf-btn--brand', type: 'button', text: 'Drop the badge', onclick: () => decide(id, 'DROPS') })));
}

async function showQueue() {
    let rows;
    try { rows = await appealQueue(); }
    catch (err) { return say(err.status === 403 ? 'This screen is for moderators.' : err.message); }
    if (rows.length === 0) return say('No open appeals.');
    section.replaceChildren(...rows.map((a) => h('button', { class: 'mod-row pf-entry sp-glass', type: 'button', onclick: () => showAppeal(a.id) },
        h('strong', { text: `${a.record.removed.username} · ${a.record.spaceName}` }),
        h('span', { text: a.note.length > 140 ? `${a.note.slice(0, 140)}…` : a.note }),
        h('time', { datetime: a.createdAt, text: day(a.createdAt) }))));
}

mountThemeSwitcher('#theme-slot', { inline: true });
currentUser().then((me) => (me ? showQueue() : location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname))))
    .catch((err) => say(err.message));
