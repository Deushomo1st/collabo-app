// Flagging a quiet collaborator, and the running cases: the clock, pleas, and the freeze-or-disband vote once time runs out.
// openFlag(about, seat, onDone) asks for a reason; casesSection(about, { me, onDecided }) lists the cases and their actions.
// Text goes in through textContent only.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { casesOf, caseStart, caseAct } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const MAX_REASON = 300;
const SHORT = 90;   // longer reasons truncate with an ellipsis you can click
const STATE = { RUNNING: ['Running', 'sp-tag--brand'], VOTING: ['Voting', 'sp-tag--brand'], RESPONDED: ['Responded', 'sp-tag--ok'], CANCELLED: ['Cancelled', 'sp-tag--muted'], DECIDED: ['Decided', 'sp-tag--muted'] };

function timeLeft(iso) {
    const ms = new Date(iso) - Date.now();
    if (ms <= 0) return 'time is up';
    const mins = Math.floor(ms / 60000);
    return mins >= 60 * 48 ? `${Math.ceil(mins / 1440)} days left` : `${Math.floor(mins / 60)}h ${mins % 60}m left`;
}

/** Ask for a reason, then flag the collaborator as quiet. */
export async function openFlag(about, seat, onDone) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Flag as quiet', html: '<h3 class="glass-blur-dialog__title"></h3><form class="sp-form"></form>' });
    panel.querySelector('h3').textContent = `Flag ${seat.person.username} as quiet`;
    const reason = h('textarea', { class: 'sp-input', rows: 3, maxlength: MAX_REASON, required: true, placeholder: 'Why? For example: no reply to the shortlist for two weeks', 'aria-label': 'Reason' });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(reason,
        h('p', { class: 'pc-hint', text: `They are nudged and have ${about.responseClockHours} hours to respond. If they do not, the founder and the other collaborators vote to freeze or disband them. The founder can cancel at any time.` }),
        err, h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Flag')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await caseStart(about.postId, seat.person.username, reason.value); close(); toast('Flagged. They have been nudged.'); onDone(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

export function casesSection(about, { me, onDecided }) {
    const postId = about.postId;
    const founder = about.role === 'FOUNDER';
    const root = h('section', {});
    const head = h('h2', { class: 'sp-h2', text: 'Quiet collaborators' });
    const list = h('div', { class: 'spc-members', 'aria-live': 'polite' });

    async function load() {
        let rows;
        try { rows = await casesOf(postId); } catch (err) { list.replaceChildren(h('p', { class: 'pc-error', text: err.message })); return; }
        const open = rows.filter((r) => r.state === 'RUNNING' || r.state === 'VOTING').length;
        head.textContent = open ? `Quiet collaborators · ${open} open` : 'Quiet collaborators';
        list.replaceChildren(...(rows.length ? rows.map(row) : [h('p', { class: 'pc-hint', text: 'Nobody has been flagged.' })]));
    }
    // a vote can freeze or disband someone, so the seats above are redrawn too
    const act = (action, r, body, done) => async () => {
        try { await caseAct(postId, r.id, action, body); if (done) toast(done); await load(); onDecided?.(); } catch (err) { toast(err.message); }
    };

    function reasonText(r) {
        const long = r.reason.length > SHORT;
        const p = h('p', { class: 'spc-idea', text: long ? `${r.reason.slice(0, SHORT)}…` : r.reason });
        if (long) { p.style.cursor = 'pointer'; p.title = 'Click to read it all'; p.addEventListener('click', () => { p.textContent = r.reason; p.style.cursor = ''; }, { once: true }); }
        return p;
    }

    function row(r) {
        const [label, tone] = STATE[r.state] || [r.state, 'sp-tag--muted'];
        const mineTarget = r.target.username === me.username;
        const running = r.state === 'RUNNING', voting = r.state === 'VOTING';
        const canPlead = running && !r.plea && !mineTarget && r.initiator.username !== me.username;
        const vote = (choice, text) => h('button', { class: `pc-btn${r.myVote === choice ? ' pc-btn--brand' : ''}`, type: 'button', text, onclick: act('vote', r, { choice }) });
        return h('div', { class: 'spc-member spc-ms sp-glass' },
            h('div', { class: 'pc-top' },
                h('a', { class: 'pc-who', href: profileHref(r.target.username), text: r.target.username }),
                h('span', { class: `sp-tag ${tone}`, text: label }),
                running && h('span', { class: 'pc-hint', text: timeLeft(r.deadline) })),
            h('p', { class: 'pc-hint' }, 'Flagged by ', h('a', { class: 'pc-who', href: profileHref(r.initiator.username), text: r.initiator.username })),
            reasonText(r),
            r.plea && h('p', { class: 'pc-hint', text: `${r.plea.by.username} entered a plea: 12 more hours to reach them.` }),
            voting && h('p', { class: 'pc-hint', text: `Freeze ${r.freezeVotes} · Disband ${r.disbandVotes} · ${r.voters} can vote. A majority decides; a tie is the founder's to break.` }),
            (running || voting) && h('div', { class: 'pc-actions' },
                running && mineTarget && h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: "I'm here", onclick: act('respond', r, undefined, 'Thanks for answering. The flag ended.') }),
                canPlead && h('button', { class: 'pc-btn', type: 'button', text: 'Plead for them (12h)', onclick: act('plea', r) }),
                voting && r.canVote && vote('FREEZE', 'Freeze'),
                voting && r.canVote && vote('DISBAND', 'Disband'),
                founder && h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Cancel', onclick: act('cancel', r) })));
    }

    root.append(head,
        h('p', { class: 'sp-sub', text: `A collaborator who goes quiet can be flagged. They have ${about.responseClockHours} hours to answer; anyone else can plead for 12 more. If they do not answer, the founder and the others vote.` }),
        list);
    load();
    return root;
}
