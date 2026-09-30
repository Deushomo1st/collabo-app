// Removal records on a profile: what happened, whether it carries a badge, the two parties' addresses, and the appeal.
// Your own records are always visible; other people's need premium (the server answers 403, shown here as a plain note).
// The two parties can address a record; the removed person can report a badge-carrying termination once.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { removalRecordsOf, removalAddress, removalAppeal } from '/js/services/api.js';
import { h, toast, day, profileHref } from '/js/services/dom.js';

const APPEAL = { OPEN: 'Appeal open', STICKS: 'Appeal decided: badge stays', DROPS: 'Appeal decided: badge dropped' };

async function textDialog({ title, hint, max, submit, send }) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: title, html: '<h3 class="glass-blur-dialog__title"></h3><form class="sp-form"></form>' });
    panel.querySelector('h3').textContent = title;
    const box = h('textarea', { class: 'sp-input', rows: 5, maxlength: max, required: true, placeholder: hint });
    const err = h('p', { class: 'pf-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(box, err, h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, submit)));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await send(box.value); close(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

function recordCard(r, { profile, viewer, reload }) {
    const iAmRemoved = profile.self;
    const iRemoved = r.removedBy.username === viewer;
    const removedSpoke = r.addresses.some((a) => a.by.username === r.removed.username);
    return h('article', { class: 'pf-entry sp-glass' },
        h('div', { class: 'pf-entry__top' },
            h('strong', { text: r.spaceName }),
            h('span', { class: `sp-tag ${r.badge ? 'sp-tag--warn' : 'sp-tag--muted'}`, text: r.badge ? 'Badge' : 'No badge' }),
            r.appeal && h('span', { class: 'sp-tag', text: APPEAL[r.appeal] || r.appeal }),
            !removedSpoke && h('span', { class: 'sp-tag sp-tag--muted', text: 'Unanswered' }),
            h('time', { datetime: r.createdAt, text: day(r.createdAt) })),
        h('p', {}, 'Removed by ', h('a', { href: profileHref(r.removedBy.username), text: r.removedBy.username }), `: ${r.reason}`),
        r.addresses.length > 0 && h('div', { class: 'pf-addresses' }, ...r.addresses.map((a) => h('div', { class: 'pf-address' },
            h('strong', {}, h('a', { href: profileHref(a.by.username), text: a.by.username }), ` · ${day(a.createdAt)}`),
            h('p', { text: a.body })))),
        (iAmRemoved || iRemoved) && h('div', { class: 'pf-entry__tools' },
            h('button', {
                class: 'pf-btn', type: 'button', text: 'Address this',
                onclick: () => textDialog({ title: 'Address this removal', hint: 'Say your side, up to 1000 characters', max: 1000, submit: 'Post',
                    send: async (body) => { await removalAddress(r.id, body); toast('Posted.'); reload(); } }),
            }),
            iAmRemoved && r.badge && !r.appeal && h('button', {
                class: 'pf-btn', type: 'button', text: 'Report this termination',
                onclick: () => textDialog({ title: 'Report this termination', hint: 'What went wrong? A moderator reads only the room around the removal.', max: 1000, submit: 'Send appeal',
                    send: async (note) => { await removalAppeal(r.id, note); toast('Appeal sent.'); reload(); } }),
            })));
}

/** Returns a box that fills itself. viewer is the signed-in username. */
export function removalRecordsSection(profile, viewer) {
    const box = h('div', { class: 'pf-list' }, h('p', { class: 'pf-empty', text: 'Loading…' }));
    const reload = async () => {
        try {
            const rows = await removalRecordsOf(profile.username);
            box.replaceChildren(...(rows.length === 0
                ? [h('p', { class: 'pf-empty', text: profile.self ? 'No removals on your record.' : 'No removals.' })]
                : rows.map((r) => recordCard(r, { profile, viewer, reload }))));
        } catch (err) {
            box.replaceChildren(h('p', { class: 'pf-empty', text: err.status === 403 ? 'Removal records of other people are a premium feature.' : err.message }));
        }
    };
    reload();
    return box;
}
