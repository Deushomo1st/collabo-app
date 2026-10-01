// A space's milestones: the accrued record of what the team has done. Fulfilling one credits everyone in the room.
// milestonesSection(space, { canLog, me }) returns a self-refreshing element. Text goes in through textContent only.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { milestonesOf, milestoneCreate, milestoneFulfil, milestoneDelete, milestoneOptOut } from '/js/services/api.js';
import { face } from '/js/services/face.js';
import { h, toast, day } from '/js/services/dom.js';

const MAX_NOTE = 500;

async function askNote(title, onDone) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Fulfil milestone', html: '<h3 class="glass-blur-dialog__title">Fulfil this milestone</h3><form class="sp-form"></form>' });
    const note = h('textarea', { class: 'sp-input', rows: 4, maxlength: MAX_NOTE, placeholder: 'What was done? (optional)', 'aria-label': 'Note' });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(h('p', { class: 'pc-hint', text: `"${title}" will be stamped on everyone in the room right now, and land in their credentials.` }), note, err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Fulfil')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await onDone(note.value); close(); } catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

export function milestonesSection(space, { canLog, me }) {
    const root = h('section', {});
    const list = h('ol', { class: 'sp-timeline', 'aria-live': 'polite' });
    let rows = [];

    async function load() {
        try { rows = await milestonesOf(space.id); } catch (err) { list.replaceChildren(h('p', { class: 'pc-error', text: err.message })); return; }
        head.textContent = `Milestones · ${rows.length}`;
        list.replaceChildren(...(rows.length ? rows.map(row) : [h('li', { class: 'pc-hint', text: 'No milestones yet.' })]));
    }
    const act = (fn, done) => async () => { try { await fn(); if (done) toast(done); await load(); } catch (err) { toast(err.message); } };

    function row(m) {
        const mineCredit = m.fulfilled && m.credited.some((p) => p.username === me.username);
        return h('li', { class: 'sp-ms sp-glass' },
            h('h3', { text: m.title }),
            m.note && h('p', { text: m.note }),
            h('div', { class: 'sp-ms__foot' },
                m.fulfilled && h('div', { class: 'sp-stack' }, ...m.credited.map((p) => face(p.username, 'sp-avatar--sm'))),
                h('time', { text: m.fulfilled ? `${day(m.fulfilledAt)} · ${m.credited.length} credited` : 'Open' }),
                canLog && !m.fulfilled && h('button', { class: 'sp-btn sp-btn--brand', type: 'button', text: 'Fulfil', style: 'margin-left:auto',
                    onclick: () => askNote(m.title, async (n) => { await milestoneFulfil(space.id, m.id, n); toast('Milestone fulfilled.'); await load(); }) }),
                canLog && !m.fulfilled && h('button', { class: 'sp-btn sp-btn--danger', type: 'button', text: 'Delete', onclick: act(() => milestoneDelete(space.id, m.id)) }),
                mineCredit && h('button', { class: 'sp-btn', type: 'button', text: 'Remove me', style: 'margin-left:auto',
                    onclick: act(() => milestoneOptOut(space.id, m.id), 'You were taken off that milestone.') })));
    }

    const head = h('h2', { class: 'sp-h2', text: 'Milestones' });
    const title = h('input', { class: 'sp-input', maxlength: 120, placeholder: 'A new objective', 'aria-label': 'Milestone title', required: true });
    const form = h('form', { class: 'ap-tools' }, title, h('button', { class: 'sp-btn sp-btn--brand', type: 'submit', text: 'Add' }));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await milestoneCreate(space.id, title.value); title.value = ''; await load(); } catch (err) { toast(err.message); }
    });
    root.append(...[head, h('p', { class: 'sp-sub', text: 'The space\x27s record, closer to a commit history than a status. Everyone in the room is credited when one is fulfilled; you can only remove yourself.' }), canLog && form, list].filter(Boolean));
    load();
    return root;
}
