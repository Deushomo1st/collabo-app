// Removal for inefficiency: flagging a quiet member, and the running processes with their clocks.
// openFlag(space, member, onDone) asks for a reason; removalsSection(space, { me }) lists processes and their actions.
// Text goes in through textContent only.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { removalsOf, removalStart, removalRespond, removalPlea, removalCancel } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const MAX_REASON = 300;
const SHORT = 90;   // longer reasons truncate with an ellipsis you can click
const STATE = { RUNNING: ['Running', 'sp-tag--brand'], RESPONDED: ['Responded', 'sp-tag--ok'], CANCELLED: ['Cancelled', 'sp-tag--muted'], COMPLETED: ['Removed', 'sp-tag--muted'] };

function timeLeft(iso) {
    const ms = new Date(iso) - Date.now();
    if (ms <= 0) return 'time is up';
    const mins = Math.floor(ms / 60000);
    return mins >= 60 * 48 ? `${Math.floor(mins / 1440)} days left` : `${Math.floor(mins / 60)}h ${mins % 60}m left`;
}

/** Ask for a reason, then flag the member as quiet. */
export async function openFlag(space, member, onDone) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Flag as quiet', html: '<h3 class="glass-blur-dialog__title"></h3><form class="sp-form"></form>' });
    panel.querySelector('h3').textContent = `Flag ${member.person.username} as quiet`;
    const reason = h('textarea', { class: 'sp-input', rows: 3, maxlength: MAX_REASON, required: true, placeholder: 'Why? For example: no reply for two weeks', 'aria-label': 'Reason' });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(reason,
        h('p', { class: 'pc-hint', text: `They are nudged in MySpace and have ${space.responseClockHours} hours to respond. If they do not, they are removed and the room shows who removed them and why. The founder can cancel at any time.` }),
        err, h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Flag')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await removalStart(space.id, member.person.username, reason.value); close(); toast('Flagged. They have been nudged.'); onDone(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

export function removalsSection(space, { me }) {
    const root = h('section', {});
    const head = h('h2', { class: 'sp-h2', text: 'Quiet members' });
    const list = h('div', { class: 'spc-members', 'aria-live': 'polite' });

    async function load() {
        let rows;
        try { rows = await removalsOf(space.id); } catch (err) { list.replaceChildren(h('p', { class: 'pc-error', text: err.message })); return; }
        const running = rows.filter((r) => r.state === 'RUNNING').length;
        head.textContent = running ? `Quiet members · ${running} running` : 'Quiet members';
        list.replaceChildren(...(rows.length ? rows.map((r) => row(r, rows)) : [h('p', { class: 'pc-hint', text: 'Nobody has been flagged.' })]));
    }
    const act = (fn, done) => async () => { try { await fn(); if (done) toast(done); await load(); } catch (err) { toast(err.message); } };

    function reasonText(r) {
        const long = r.reason.length > SHORT;
        const p = h('p', { class: 'spc-idea', text: long ? `${r.reason.slice(0, SHORT)}…` : r.reason });
        if (long) { p.style.cursor = 'pointer'; p.title = 'Click to read it all'; p.addEventListener('click', () => { p.textContent = r.reason; p.style.cursor = ''; }, { once: true }); }
        return p;
    }

    function row(r) {
        const [label, tone] = STATE[r.state] || [r.state, 'sp-tag--muted'];
        const mineTarget = r.target.username === me.username;
        const standing = r.plea != null;
        const canPlead = space.pleasEnabled && !standing && !mineTarget && r.initiator.username !== me.username;
        return h('div', { class: 'spc-member spc-ms sp-glass' },
            h('div', { class: 'pc-top' },
                h('a', { class: 'pc-who', href: profileHref(r.target.username), text: r.target.username }),
                h('span', { class: `sp-tag ${tone}`, text: label }),
                r.state === 'RUNNING' && h('span', { class: 'pc-hint', text: timeLeft(r.deadline) })),
            h('p', { class: 'pc-hint' }, 'Flagged by ', h('a', { class: 'pc-who', href: profileHref(r.initiator.username), text: r.initiator.username })),
            reasonText(r),
            standing && h('p', { class: 'pc-hint', text: `${r.plea.by.username} entered a plea: 12 more hours to reach them.` }),
            r.state === 'RUNNING' && h('div', { class: 'pc-actions' },
                mineTarget && h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: "I'm here", onclick: act(() => removalRespond(space.id, r.id), 'Thanks for answering. The process ended.') }),
                canPlead && h('button', { class: 'pc-btn', type: 'button', text: 'Plead for them (12h)', onclick: act(() => removalPlea(space.id, r.id)) }),
                space.role === 'OWNER' && h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Cancel process', onclick: act(() => removalCancel(space.id, r.id)) })));
    }

    root.append(head,
        h('p', { class: 'sp-sub', text: `A member who goes quiet can be flagged. They have ${space.responseClockHours} hours to answer${space.pleasEnabled ? '; anyone else can plead for 12 more' : ''}.` }),
        list);
    load();
    return root;
}
