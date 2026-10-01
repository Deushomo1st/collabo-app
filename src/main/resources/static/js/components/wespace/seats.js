// The collaborators tab: who holds each seat and what you can do about it. The founder can freeze, unfreeze or disband;
// any live collaborator can nudge or flag. Text goes in through textContent only.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { face } from '/js/services/face.js';
import { collaboratorAct, collaboratorRemove } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';
import { openFlag } from '/js/components/wespace/cases.js';

const YARNS = '/HTML-pages/yarnspaces.html';
const MAX_REASON = 300;
const STATE = { ACTIVE: null, FROZEN: ['Frozen', 'sp-tag--muted'], DISBANDED: ['Disbanded', 'sp-tag--muted'], INVITED: ['Asked', 'sp-tag--brand'] };

/** Disbanding needs a stated reason. */
async function openDisband(postId, username, onDone) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Disband', html: '<h3 class="glass-blur-dialog__title"></h3><form class="sp-form"></form>' });
    panel.querySelector('h3').textContent = `Disband ${username}`;
    const reason = h('textarea', { class: 'sp-input', rows: 3, maxlength: MAX_REASON, required: true, placeholder: 'Why? For example: inactive for a month', 'aria-label': 'Reason' });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(reason,
        h('p', { class: 'pc-hint', text: 'The reason is posted in the room and sent to them. Their spot stays open: they can come back if you ask them again.' }),
        err, h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Disband')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await collaboratorAct(postId, username, 'disband', reason.value); close(); toast(`${username} was disbanded.`); onDone(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

function seatRow(about, me, s, refresh) {
    const postId = about.postId;
    const name = s.person.username;
    const tag = STATE[s.state];
    const mineRow = name === me.username;
    const founder = about.role === 'FOUNDER';
    const live = s.state === 'ACTIVE' && !s.founder;
    const act = (action, done) => async () => { try { await collaboratorAct(postId, name, action); toast(done); await refresh(); } catch (err) { toast(err.message); } };
    const btn = (text, fn) => h('button', { class: 'sp-btn', type: 'button', text, onclick: fn });
    return h('div', { class: 'spc-member' },
        face(name, 'sp-avatar--sm'),
        h('a', { class: 'pc-who', href: profileHref(name), text: name }),
        s.founder && h('span', { class: 'sp-tag sp-tag--brand', text: 'Founder' }),
        tag && h('span', { class: `sp-tag ${tag[1]}`, text: tag[0] }),
        s.reason && h('small', { class: 'pc-hint', text: s.reason }),
        h('span', { class: 'spc-perms' },
            live && !mineRow && about.role !== 'FROZEN' && btn('Nudge', act('nudge', `${name} was nudged.`)),
            live && !mineRow && about.role !== 'FROZEN' && btn('Flag as quiet', () => openFlag(about, s, refresh)),
            founder && live && btn('Freeze', act('freeze', `${name} was frozen.`)),
            founder && s.state === 'FROZEN' && btn('Unfreeze', act('unfreeze', `${name} was unfrozen.`)),
            founder && (live || s.state === 'FROZEN') && btn('Disband', () => openDisband(postId, name, refresh)),
            mineRow && !s.founder && about.role !== 'FOUNDER' && btn('Step down', async () => { try { await collaboratorRemove(postId, name); location.replace(YARNS); } catch (err) { toast(err.message); } })));
}

export function seatsSection(about, { me, refresh }) {
    const count = about.seats.filter((s) => s.state === 'ACTIVE' || s.state === 'FROZEN').length;
    return h('section', {},
        h('h2', { class: 'sp-h2', text: `Collaborators · ${count}` }),
        h('p', { class: 'sp-sub', text: 'The people who decide who joins. Frozen collaborators read but do not write or review. A disbanded spot stays open.' }),
        h('div', { class: 'spc-members' }, ...about.seats.map((s) => seatRow(about, me, s, refresh))));
}
