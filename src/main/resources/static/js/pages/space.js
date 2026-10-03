// A Workspace's screen: the Workspace design on real data. The header names it (with the Workspace tag and the team's faces) and carries the sections as a slider
// (Room, About, Members, Milestones, Payments, Settings); the black Tasks pill hangs under it. Room is the team's chat in Yarns. Accepted applicants read it all and join from About.
// All network calls live in js/services/api.js; text goes in through textContent only.
import '/js/services/live.js';   // keeps the live socket open, so yarns sent to you are acknowledged as delivered from any page
import { membersSection } from '/js/components/space/members.js';
import { face } from '/js/services/face.js';
import { mountIsland } from '/js/components/space/task-island.js';
import { feedTabs } from '/js/services/feed-tabs.js';
import { milestonesSection } from '/js/components/space/milestones.js';
import { paymentsSection } from '/js/components/space/payments.js';
import { removalsSection } from '/js/components/space/removals.js';
import { openSettings } from '/js/components/space/settings.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { currentUser, spaceById, spaceOfPost, spaceJoin, spaceLeave, spaceMembers } from '/js/services/api.js';
import { skeletonCards } from '/js/services/skeleton.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const ROLE = { OWNER: 'Owner', MEMBER: 'Member', APPLICANT: 'Accepted' };

const main = () => document.getElementById('space');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
const say = (text) => main().replaceChildren(h('p', { class: 'gz-empty', text }));

let space, members, me, island = null, tabs = null, tabsRole = null;
const INFO = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>';
const SECTION_IDS = ['about', 'members', 'milestones', 'payments', 'settings'];
const hereId = () => SECTION_IDS.find((id) => location.hash === '#' + id) || 'about';
const roomHref = () => `/HTML-pages/yarnspaces.html#t/${space.threadId}`;
/** The sections this person sees, in order: [id, label]. Room and Payments are the team's; an accepted applicant reads the rest. */
function sectionsFor() {
    const inRoom = space.role !== 'APPLICANT';
    return [inRoom && space.threadId && ['room', 'Room'], ['about', 'About'], ['members', 'Members'], ['milestones', 'Milestones'], inRoom && ['payments', 'Payments'], ['settings', 'Settings']].filter(Boolean);
}

async function refresh() {
    const params = new URLSearchParams(location.search);
    space = params.get('id') ? await spaceById(params.get('id')) : await spaceOfPost(params.get('post'));
    members = await spaceMembers(space.id);   // accepted applicants may read the list too
    draw();
}

async function act(fn, done) {
    try { await fn(); if (done) toast(done); await refresh(); } catch (err) { toast(err.message); }
}

const row = (title, text, control) => h('div', { class: 'sp-setting sp-glass' }, h('div', { class: 'sp-setting__text' }, h('strong', { text: title }), h('span', { text })), control);
const day = (iso) => new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

/** The Workspace prototype's settings rows, read-only here; whoever may edit gets the button. */
function settingsSection(mayEdit) {
    return h('section', {},
        h('h2', { class: 'sp-h2', text: 'Space settings' }),
        h('p', { class: 'sp-sub', text: 'Every change is announced in the room. That is the difference between governance and manipulation.' }),
        row('Response clock', 'How long a quiet member has to answer a nudge. Hard floor of 48 hours.', h('span', { class: 'sp-tag', text: `${space.responseClockHours} hours` })),
        row('Pleas', 'A plea buys a quiet member 12 hours. One at a time.', h('span', { class: `sp-tag ${space.pleasEnabled ? 'sp-tag--ok' : ''}`, text: space.pleasEnabled ? 'On' : 'Off' })),
        mayEdit && h('button', { class: 'sp-btn sp-btn--brand', type: 'button', text: 'Edit settings', onclick: () => openSettings(space, refresh) }));
}

/** The header: the name, the Workspace tag, the faces (a tap opens Members), and under them what it is and how many are in it. */
function drawHeader() {
    document.title = `COLLABO — ${space.name}`;
    const stack = h('div', { class: 'sp-stack', id: 'header-stack', role: 'link', tabindex: '0', 'aria-label': 'Open the members' }, ...members.slice(0, 6).map((m) => face(m.person.username)));
    document.getElementById('header-info').replaceChildren(
        h('div', { class: 'sp-title__row' }, h('h1', { id: 'space-name', text: space.name }), h('span', { class: 'sp-tag sp-tag--brand', text: 'Workspace' }), stack),
        h('p', { text: `${space.postTitle} · ${members.length} ${members.length === 1 ? 'person' : 'people'}` }));
}

function draw() {
    if (tabsRole && tabsRole !== space.role) return location.reload();   // joining changes which sections there are
    drawHeader();
    const list = sectionsFor();
    if (!tabs) {
        tabsRole = space.role;
        tabs = feedTabs(document.getElementById('ws-tabs'), {
            labels: list.map(([, label]) => label), at: () => list.findIndex(([id]) => id === hereId()),
            go: (i) => { const [id] = list[i]; if (id === 'room') location.href = roomHref(); else location.hash = '#' + id; },
        });
        const info = h('button', { class: 'nav-gear ws-gear', type: 'button', title: 'Members', 'aria-label': 'Workspace info: the members', onclick: () => { location.hash = '#members'; } });
        info.innerHTML = INFO;   // fixed markup, no user text
        document.querySelector('.sp-header').append(info);   // beside the menu; the Return and the menu come from nav-mode.js
    }
    tabs.sync();
    const gear = document.querySelector('.ws-gear');
    if (gear) gear.hidden = hereId() === 'members';   // the (i) leads to Members, so it has nothing to do once you are there
    const mine = members.find((m) => m.person.username === me.username);
    const can = (perm) => !!mine && mine.permissions.includes(perm);
    const inRoom = space.role !== 'APPLICANT';   // payments are the team's business; the record of milestones is open to accepted applicants too
    // Only the open section is built.
    const body = {
        about: () => [
            space.role === 'APPLICANT' && h('section', { class: 'spc-card spc-join sp-glass' },
                h('h2', { text: 'You were accepted' }),
                h('p', { class: 'pc-hint', text: 'You can read everything here. Joining is your call: it puts you on the team and in the room.' }),
                h('div', { class: 'pc-actions' }, h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Join space',
                    onclick: () => act(() => spaceJoin(space.id), 'You joined the space.') }))),
            h('section', { class: 'spc-card sp-glass' },
                h('h2', { text: space.postTitle }),
                h('p', { class: 'pc-hint' }, 'The idea, by ', h('a', { class: 'pc-who', href: profileHref(space.owner.username), text: space.owner.username })),
                h('p', { class: 'spc-idea', text: space.postBody })),
            // the Workspace design's info rows: who is in it, when it was formed, and the rules the team runs on
            h('section', {},
                h('h2', { class: 'sp-h2', text: 'At a glance' }),
                row('You', 'Your place in this space.', h('span', { class: 'sp-tag sp-tag--brand', text: ROLE[space.role] || space.role })),
                row('Team', `${members.length} ${members.length === 1 ? 'person' : 'people'}. Open Members for roles and permissions.`, h('div', { class: 'sp-stack' }, ...members.slice(0, 5).map((m) => face(m.person.username)))),
                row('Formed', `Born from the post “${space.postTitle}”.`, h('span', { class: 'sp-tag', text: day(space.createdAt) })),
                row('Response clock', 'How long a quiet member has to answer a nudge.', h('span', { class: 'sp-tag', text: `${space.responseClockHours} hours` })),
                row('Pleas', 'A plea buys a quiet member 12 hours.', h('span', { class: `sp-tag ${space.pleasEnabled ? 'sp-tag--ok' : ''}`, text: space.pleasEnabled ? 'On' : 'Off' }))),
            space.threadId && inRoom && h('a', { class: 'sp-btn sp-btn--brand', href: roomHref(), text: 'Open the room' }),
            space.role === 'MEMBER' && !space.canManage && h('div', { class: 'pc-actions' }, h('button', { class: 'sp-btn sp-btn--danger', type: 'button', text: 'Leave space',
                onclick: () => act(() => spaceLeave(space.id), 'You left the space.') }))],
        members: () => [membersSection(space, { members, me, refresh }), inRoom && removalsSection(space, { me })],
        milestones: () => [milestonesSection(space, { canLog: can('LOG_MILESTONES'), me })],
        payments: () => [inRoom ? paymentsSection(space, { canLog: can('LOG_PAYMENTS'), me, members }) : h('p', { class: 'gz-empty', text: 'Payments are the team\u2019s business.' })],
        settings: () => [settingsSection(can('EDIT_SETTINGS') || space.role === 'OWNER')],
    };
    main().replaceChildren(...body[hereId()]().filter(Boolean));
    if (inRoom && !island) {   // the Tasks pill: the same one the room carries, asked for once
        island = {};
        mountIsland(document.getElementById('island'), space.postId, me).then((i) => { island = i; });
    } else island?.reload?.();
}

async function boot() {
    main().replaceChildren(...skeletonCards(2));   // the cards' shape while the space loads
    me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    mountMainNav(me.username);
    window.addEventListener('hashchange', () => { if (space) draw(); window.scrollTo({ top: 0, behavior: 'instant' }); });
    const shell = document.querySelector('.sp-shell');   // the header shrinks once you scroll (with some give, so its height change cannot flicker it)
    window.addEventListener('scroll', () => { const y = window.scrollY; if (y > 70) shell.classList.add('is-compact'); else if (y < 10) shell.classList.remove('is-compact'); }, { passive: true });
    document.querySelector('.sp-header').addEventListener('click', (e) => {   // the faces open Members, the rest of the header Settings
        if (e.target.closest('.nav-return, .nav-gear, #bell-slot, .ws-tabs')) return;
        location.hash = e.target.closest('#header-stack') ? '#members' : '#settings';
    });
    try { await refresh(); }
    catch (err) {
        if (err.status === 401) return toLogin();
        say(err.status === 404 ? 'This space does not exist, or it is not open to you.' : err.message);
    }
}

boot();
