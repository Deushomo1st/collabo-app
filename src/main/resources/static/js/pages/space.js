// A space: the idea it grew from, joining (for accepted applicants), and the people in it.
// All network calls live in js/services/api.js; text goes in through textContent only.
import '/js/services/live.js';   // keeps the live socket open, so yarns sent to you are acknowledged as delivered from any page
import { membersSection } from '/js/components/space/members.js';
import { face } from '/js/services/face.js';
import { tasksPanel } from '/js/components/space/tasks.js';
import { milestonesSection } from '/js/components/space/milestones.js';
import { paymentsSection } from '/js/components/space/payments.js';
import { removalsSection } from '/js/components/space/removals.js';
import { openSettings } from '/js/components/space/settings.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { currentUser, spaceById, spaceOfPost, spaceJoin, spaceLeave, spaceMembers } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const ROLE = { OWNER: 'Owner', MEMBER: 'Member', APPLICANT: 'Accepted' };

const main = () => document.getElementById('space');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
const say = (text) => main().replaceChildren(h('p', { class: 'gz-empty', text }));

let space, members, me, tab = 'about';

async function refresh() {
    const params = new URLSearchParams(location.search);
    space = params.get('id') ? await spaceById(params.get('id')) : await spaceOfPost(params.get('post'));
    members = await spaceMembers(space.id);   // accepted applicants may read the list too
    draw();
}

async function act(fn, done) {
    try { await fn(); if (done) toast(done); await refresh(); } catch (err) { toast(err.message); }
}

/** The Workspace prototype's settings rows, read-only here; whoever may edit gets the button. */
function settingsSection(mayEdit) {
    const row = (title, text, control) => h('div', { class: 'sp-setting sp-glass' }, h('div', { class: 'sp-setting__text' }, h('strong', { text: title }), h('span', { text })), control);
    return h('section', {},
        h('h2', { class: 'sp-h2', text: 'Space settings' }),
        h('p', { class: 'sp-sub', text: 'Every change is announced in the room. That is the difference between governance and manipulation.' }),
        row('Response clock', 'How long a quiet member has to answer a nudge. Hard floor of 48 hours.', h('span', { class: 'sp-tag', text: `${space.responseClockHours} hours` })),
        row('Pleas', 'A plea buys a quiet member 12 hours. One at a time.', h('span', { class: `sp-tag ${space.pleasEnabled ? 'sp-tag--ok' : ''}`, text: space.pleasEnabled ? 'On' : 'Off' })),
        mayEdit && h('button', { class: 'sp-btn sp-btn--brand', type: 'button', text: 'Edit settings', onclick: () => openSettings(space, refresh) }));
}

function draw() {
    document.getElementById('space-name').textContent = space.name;
    const tag = document.getElementById('space-role');
    tag.textContent = ROLE[space.role] || space.role; tag.hidden = false;
    document.title = `COLLABO — ${space.name}`;
    const mine = members.find((m) => m.person.username === me.username);
    const can = (perm) => !!mine && mine.permissions.includes(perm);
    const inRoom = space.role !== 'APPLICANT';   // payments are the team's business; the record of milestones is open to accepted applicants too
    // Only the open section is built; the tab bar sits above it.
    const sections = [
        ['about', 'About', () => [
            space.role === 'APPLICANT' && h('section', { class: 'spc-card spc-join sp-glass' },
                h('h2', { text: 'You were accepted' }),
                h('p', { class: 'pc-hint', text: 'You can read everything here. Joining is your call: it puts you on the team and in the room.' }),
                h('div', { class: 'pc-actions' }, h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Join space',
                    onclick: () => act(() => spaceJoin(space.id), 'You joined the space.') }))),
            h('section', { class: 'spc-card sp-glass' },
                h('h2', { text: space.postTitle }),
                h('p', { class: 'pc-hint' }, 'The idea, by ', h('a', { class: 'pc-who', href: profileHref(space.owner.username), text: space.owner.username })),
                h('p', { class: 'spc-idea', text: space.postBody })),
            space.threadId && h('a', { class: 'sp-btn sp-btn--brand', href: `/HTML-pages/yarnspaces.html#t/${space.threadId}`, text: 'Open the room' }),
            space.role === 'MEMBER' && !space.canManage && h('div', { class: 'pc-actions' }, h('button', { class: 'sp-btn sp-btn--danger', type: 'button', text: 'Leave space',
                onclick: () => act(() => spaceLeave(space.id), 'You left the space.') }))]],
        ['team', `Team ${members.length}`, () => [membersSection(space, { members, me, refresh }), inRoom && removalsSection(space, { me })]],
        inRoom && ['tasks', 'Tasks', () => [h('section', {}, h('h2', { class: 'sp-h2', text: 'Tasks' }), h('div', { class: 'spc-card sp-glass' }, tasksPanel(space, { me, members })))]],
        ['milestones', 'Milestones', () => [milestonesSection(space, { canLog: can('LOG_MILESTONES'), me })]],
        inRoom && ['payments', 'Payments', () => [paymentsSection(space, { canLog: can('LOG_PAYMENTS'), me, members })]],
        ['settings', 'Settings', () => [settingsSection(can('EDIT_SETTINGS') || space.role === 'OWNER')]],
    ].filter(Boolean);
    if (!sections.some(([k]) => k === tab)) tab = 'about';
    const bar = h('nav', { class: 'sp-tt', role: 'tablist' }, ...sections.map(([k, label]) =>
        h('button', { class: k === tab ? 'is-on' : '', type: 'button', role: 'tab', 'aria-selected': String(k === tab), text: label, onclick: () => { tab = k; draw(); } })));
    main().replaceChildren(bar, ...sections.find(([k]) => k === tab)[2]().filter(Boolean));
    bar.querySelector('.is-on')?.scrollIntoView({ block: 'nearest', inline: 'center' });
    document.getElementById('header-stack').replaceChildren(...members.slice(0, 6).map((m) => face(m.person.username)));
}

async function boot() {
    me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    mountMainNav(me.username);
    try { await refresh(); }
    catch (err) {
        if (err.status === 401) return toLogin();
        say(err.status === 404 ? 'This space does not exist, or it is not open to you.' : err.message);
    }
}

boot();
