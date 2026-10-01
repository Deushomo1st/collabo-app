// Reports: what members sent from the Report page (a write-up, and screenshots or videos). Mark one resolved when it is dealt with.
// Files need the admin key, so they come down as blobs (api.reportMediaUrl). Text goes in through textContent only.
import { h } from '/js/services/dom.js';
import * as api from '/js/services/admin-api.js';
import { notify, ago } from './ui.js';

export async function reportsView() {
    const root = h('div', { class: 'ad-view' });
    let all = await api.reports(), filter = 'open';

    const urls = [];   // object URLs made for this view, released when the list is drawn again
    function media(reportId, m) {
        const box = h('div', { class: 'ad-media__item', text: 'Loading…' });
        api.reportMediaUrl(reportId, m.id).then((url) => {
            urls.push(url);
            box.replaceChildren(m.kind === 'VIDEO'
                ? h('video', { src: url, controls: true, preload: 'metadata', playsinline: true })
                : h('a', { href: url, target: '_blank', rel: 'noopener', title: 'Open full size' }, h('img', { src: url, alt: 'Screenshot from the report' })));
        }).catch((e) => { if (e instanceof api.AdminAuthError) throw e; box.textContent = 'Could not load.'; });
        return box;
    }

    async function toggle(r) {
        try { await api.resolveReport(r.id, !r.resolved); all = await api.reports(); draw(); notify(r.resolved ? 'Reopened.' : 'Marked resolved.'); }
        catch (e) { if (e instanceof api.AdminAuthError) throw e; notify(e.message, true); }
    }

    function card(r) {
        return h('article', { class: 'ad-card' },
            h('div', {}, h('strong', { text: r.reporter }), ' ', h('span', { class: 'ad-sub', text: ago(r.createdAt) }),
                h('span', { class: `ad-tag ${r.resolved ? 'is-ok' : 'is-bad'}`, style: 'float:right', text: r.resolved ? 'Resolved' : 'Open' })),
            r.pageUrl && h('div', { class: 'ad-sub', text: `On ${r.pageUrl}` }),
            h('p', { class: 'ad-report__text', text: r.summary }),
            r.media.length > 0 && h('div', { class: 'ad-media' }, ...r.media.map((m) => media(r.id, m))),
            h('div', {}, h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: r.resolved ? 'Reopen' : 'Mark resolved', onclick: () => toggle(r) })));
    }

    function draw() {
        urls.splice(0).forEach((u) => URL.revokeObjectURL(u));
        const shown = all.filter((r) => r.resolved === (filter === 'resolved'));
        const cards = shown.map(card);   // each media tile fills itself in when its file arrives
        root.replaceChildren(
            h('div', { class: 'ad-head' }, h('h2', { text: 'Reports' })),
            h('p', { class: 'ad-lead', text: 'Problems members reported from the Report page, with their screenshots or videos.' }),
            h('div', { class: 'ad-toolbar' }, h('div', { class: 'ad-chips' }, ...[['open', 'Open'], ['resolved', 'Resolved']].map(([key, label]) =>
                h('button', { class: `ad-chip${filter === key ? ' is-on' : ''}`, type: 'button', text: label, onclick: () => { filter = key; draw(); } }))),
                h('span', { class: 'ad-count', text: `${shown.length} ${filter}` })),
            shown.length ? h('div', { class: 'ad-cards' }, ...cards) : h('p', { class: 'ad-empty', text: filter === 'open' ? 'No open reports.' : 'Nothing resolved yet.' }));
    }

    draw();
    return root;
}
