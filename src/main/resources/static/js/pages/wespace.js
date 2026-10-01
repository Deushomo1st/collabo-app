// A WeSpace's "about the group": the idea, who decides, and the state of each seat. Reached as wespace.html?post=<post id>
// from the room's header bar. The founder can nudge, freeze or disband; a collaborator can nudge. Text goes in through textContent only.
import '/js/services/live.js';
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { fanActions } from '/js/services/fan-actions.js';
import { face } from '/js/services/face.js';
import { CHAT, PERSON, PEOPLE, BRIEFCASE, svg } from '/js/services/icons.js';
import { currentUser, wespaceAbout, wespaceClock, collaboratorAct, collaboratorRemove } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';
import { openFlag, casesSection } from '/js/components/wespace/cases.js';

const YARNS = '/HTML-pages/yarnspaces.html';
const MAX_REASON = 300;
const MIN_CLOCK = 48;
const STATE = { ACTIVE: null, FROZEN: ['Frozen', 'sp-tag--muted'], DISBANDED: ['Disbanded', 'sp-tag--muted'], INVITED: ['Asked', 'sp-tag--brand'] };
const ROLE = { FOUNDER: 'Founder', COLLABORATOR: 'Collaborator', FROZEN: 'Frozen' };

const postId = new URLSearchParams(location.search).get('post');
const main = () => document.getElementById('space');
let about, me;

async function refresh() {
    about = await wespaceAbout(postId);
    draw();
}

const act = (username, action, done) => async () => { try { await collaboratorAct(postId, username, action); toast(done); await refresh(); } catch (err) { toast(err.message); } };

/** Disbanding needs a stated reason. */
async function openDisband(username) {
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
        try { await collaboratorAct(postId, username, 'disband', reason.value); close(); toast(`${username} was disbanded.`); refresh(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

function seatRow(s) {
    const name = s.person.username;
    const tag = STATE[s.state];
    const mineRow = name === me.username;
    const founder = about.role === 'FOUNDER';
    const live = s.state === 'ACTIVE' && !s.founder;
    const btn = (text, fn) => h('button', { class: 'sp-btn', type: 'button', text, onclick: fn });
    return h('div', { class: 'spc-member' },
        face(name, 'sp-avatar--sm'),
        h('a', { class: 'pc-who', href: profileHref(name), text: name }),
        s.founder && h('span', { class: 'sp-tag sp-tag--brand', text: 'Founder' }),
        tag && h('span', { class: `sp-tag ${tag[1]}`, text: tag[0] }),
        s.reason && h('small', { class: 'pc-hint', text: s.reason }),
        h('span', { class: 'spc-perms' },
            live && !mineRow && about.role !== 'FROZEN' && btn('Nudge', act(name, 'nudge', `${name} was nudged.`)),
            live && !mineRow && about.role !== 'FROZEN' && btn('Flag as quiet', () => openFlag(about, s, refresh)),
            founder && live && btn('Freeze', act(name, 'freeze', `${name} was frozen.`)),
            founder && s.state === 'FROZEN' && btn('Unfreeze', act(name, 'unfreeze', `${name} was unfrozen.`)),
            founder && (live || s.state === 'FROZEN') && btn('Disband', () => openDisband(name)),
            mineRow && !s.founder && about.role !== 'FOUNDER' && btn('Step down', async () => { try { await collaboratorRemove(postId, name); location.replace(YARNS); } catch (err) { toast(err.message); } })));
}

/** The founder sets how long a flagged collaborator has to answer; everyone else just reads it. Never under 48 hours. */
function clockRow() {
    const text = h('p', { class: 'sp-sub', text: `Response clock: ${about.responseClockHours} hours. A flagged collaborator has this long to answer before the others vote.` });
    if (about.role !== 'FOUNDER') return text;
    const hours = h('input', { class: 'sp-input', type: 'number', min: MIN_CLOCK, max: 8760, value: about.responseClockHours, 'aria-label': 'Response clock in hours' });
    const save = async () => { try { await wespaceClock(postId, Number(hours.value)); toast('Response clock updated.'); await refresh(); } catch (err) { toast(err.message); } };
    return h('div', {}, text, h('span', { class: 'spc-perms' }, hours, h('button', { class: 'sp-btn', type: 'button', text: 'Set clock', onclick: save })));
}

function draw() {
    document.title = `COLLABO — ${about.title}`;
    document.getElementById('space-name').textContent = about.title;
    const tag = document.getElementById('space-role');
    tag.textContent = ROLE[about.role]; tag.hidden = false;
    main().replaceChildren(...[
        h('section', { class: 'spc-card sp-glass' },
            h('h2', { text: about.title }),
            h('p', { class: 'pc-hint', text: `Idea status: ${about.postStatus}` }),
            h('p', { class: 'spc-idea', text: about.body })),
        h('section', {},
            h('h2', { class: 'sp-h2', text: `Collaborators · ${about.seats.filter((s) => s.state === 'ACTIVE' || s.state === 'FROZEN').length}` }),
            h('p', { class: 'sp-sub', text: 'The people who decide who joins. Frozen collaborators read but do not write or review. A disbanded spot stays open.' }),
            h('div', { class: 'spc-members' }, ...about.seats.map(seatRow))),
        about.role !== 'FROZEN' && clockRow(),
        about.role !== 'FROZEN' && casesSection(about, { me, onDecided: refresh }),
        about.threadId && h('a', { class: 'sp-btn sp-btn--brand', href: `${YARNS}#t/${about.threadId}`, text: 'Open the room' }),
        about.role === 'FOUNDER' && h('a', { class: 'sp-btn', href: `/HTML-pages/applicants.html?post=${postId}`, text: 'Review applicants' }),
        about.spaceId && h('a', { class: 'sp-btn', href: `/HTML-pages/space.html?id=${about.spaceId}`, text: 'Open the Workspace' }),
    ].filter(Boolean));
}

async function boot() {
    me = await currentUser().catch(() => null);
    if (!me) return location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
    mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, holdActions: fanActions(), visible: 3, activeIndex: 2, restIcon: svg(PEOPLE),
        links: ['All yarns', 'MySpaces', 'WeSpaces', 'WorkSpaces'],
        hrefs: [YARNS, `${YARNS}#myspace`, `${YARNS}#wespace`, `${YARNS}#workspace`],
        icons: [CHAT, PERSON, PEOPLE, BRIEFCASE].map(svg),
        onChange: (_l, href) => { location.href = href.slice(href.indexOf('/HTML-pages')); },
    });
    try { await refresh(); }
    catch (err) { main().replaceChildren(h('p', { class: 'gz-empty', text: err.status === 404 ? 'This group does not exist, or it is not open to you.' : err.message })); }
}

mountThemeSwitcher('#theme-slot', { inline: true, collapse: true });
boot();
