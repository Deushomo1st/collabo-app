// A space: the idea it grew from, joining (for accepted applicants), and the people in it.
// All network calls live in js/services/api.js; text goes in through textContent only.
import '/js/services/live.js';   // keeps the live socket open, so yarns sent to you are acknowledged as delivered from any page
import { mountThemeSwitcher, openThemeSettings } from '/js/components/theme-switcher/theme-switcher.js';
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { milestonesSection } from '/js/components/space/milestones.js';
import { paymentsSection } from '/js/components/space/payments.js';
import { removalsSection, openFlag } from '/js/components/space/removals.js';
import { openSettings } from '/js/components/space/settings.js';
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { fanActions } from '/js/services/fan-actions.js';
import { GEAR, CHAT, PERSON, PEOPLE, BRIEFCASE, svg } from '/js/services/icons.js';
import { currentUser, spaceById, spaceOfPost, spaceJoin, spaceLeave, spaceMembers, spaceMemberUpdate, spaceMemberRemove } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const ROLE = { OWNER: 'Owner', MEMBER: 'Member', APPLICANT: 'Accepted' };
const PERM = { LOG_MILESTONES: 'log milestones', LOG_PAYMENTS: 'log payments', ACCEPT_MEMBERS: 'accept and remove members',
    EDIT_SETTINGS: 'edit settings', POST_IN_ROOM: 'post and pin', MANAGE_RECRUITMENT: 'manage recruitment' };

const main = () => document.getElementById('space');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
const say = (text) => main().replaceChildren(h('p', { class: 'gz-empty', text }));

const WEIGHTY = ['ACCEPT_MEMBERS', 'EDIT_SETTINGS', 'POST_IN_ROOM', 'MANAGE_RECRUITMENT'];
let space, members, me;

async function refresh() {
    const params = new URLSearchParams(location.search);
    space = params.get('id') ? await spaceById(params.get('id')) : await spaceOfPost(params.get('post'));
    members = await spaceMembers(space.id);   // accepted applicants may read the list too
    draw();
}

async function act(fn, done) {
    try { await fn(); if (done) toast(done); await refresh(); } catch (err) { toast(err.message); }
}

/** Role title and permissions for one member. The weighty ones need an explicit tick. */
async function editMember(m) {
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: 'Edit member', html: '<h3 class="glass-blur-dialog__title"></h3><form class="sp-form"></form>' });
    panel.querySelector('h3').textContent = `Role for ${m.person.username}`;
    const title = h('input', { class: 'sp-input', maxlength: 40, value: m.title, placeholder: 'Role title, e.g. Sound designer', 'aria-label': 'Role title' });
    const boxes = Object.entries(PERM).map(([key, text]) => ({ key, box: h('input', { type: 'checkbox', checked: m.permissions.includes(key) }), text }));
    const confirm = h('input', { type: 'checkbox' });
    const warn = h('label', { class: 'spc-check pc-error' }, confirm, ' I understand this person can change the team itself.');
    const err = h('p', { class: 'pc-error', hidden: true });
    const showWarn = () => { warn.hidden = !boxes.some((b) => WEIGHTY.includes(b.key) && b.box.checked && !m.permissions.includes(b.key)); };
    boxes.forEach((b) => b.box.addEventListener('change', showWarn));
    showWarn();
    const form = panel.querySelector('form');
    form.append(title, ...boxes.map((b) => h('label', { class: 'spc-check' }, b.box, ` ${b.text}`)), warn, err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Save')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await spaceMemberUpdate(space.id, m.person.username, { title: title.value, permissions: boxes.filter((b) => b.box.checked).map((b) => b.key), confirm: confirm.checked });
            close(); await refresh();
        } catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

function memberRow(m) {
    const mine = m.person.username === me.username;
    return h('div', { class: 'spc-member' },
        h('a', { class: 'pc-who', href: profileHref(m.person.username), text: m.person.username }),
        m.person.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: m.person.preferredTitle }),
        m.title && h('span', { class: `sp-tag ${m.owner ? 'sp-tag--ok' : 'sp-tag--muted'}`, text: m.title }),
        !m.owner && m.permissions.length > 0 && h('span', { class: 'spc-perms', text: m.permissions.map((p) => PERM[p] || p).join(' · ') }),
        space.role !== 'APPLICANT' && !m.owner && !mine && m.title !== 'Collaborator' && h('button', { class: 'pc-btn', type: 'button', text: 'Flag as quiet', onclick: () => openFlag(space, m, refresh) }),
        space.canManage && !m.owner && h('button', { class: 'pc-btn', type: 'button', text: 'Edit', onclick: () => editMember(m) }),
        space.canManage && !m.owner && !mine && h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Remove', onclick: () => act(() => spaceMemberRemove(space.id, m.person.username), `${m.person.username} was removed.`) }));
}

function draw() {
    document.getElementById('space-name').textContent = space.name;
    const tag = document.getElementById('space-role');
    tag.textContent = ROLE[space.role] || space.role; tag.hidden = false;
    document.title = `COLLABO — ${space.name}`;
    const mine = members.find((m) => m.person.username === me.username);
    const can = (perm) => !!mine && mine.permissions.includes(perm);
    const inRoom = space.role !== 'APPLICANT';   // payments are the team's business; the record of milestones is open to accepted applicants too
    main().replaceChildren(...[
        space.role === 'APPLICANT' && h('section', { class: 'spc-card spc-join sp-glass' },
            h('h2', { text: 'You were accepted' }),
            h('p', { class: 'pc-hint', text: 'You can read everything here. Joining is your call: it puts you on the team and in the room.' }),
            h('div', { class: 'pc-actions' }, h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Join space',
                onclick: () => act(() => spaceJoin(space.id), 'You joined the space.') }))),
        h('section', { class: 'spc-card sp-glass' },
            h('h2', { text: space.postTitle }),
            h('p', { class: 'pc-hint' }, 'The idea, by ', h('a', { class: 'pc-who', href: profileHref(space.owner.username), text: space.owner.username })),
            h('p', { class: 'spc-idea', text: space.postBody }),
            h('p', { class: 'pc-hint', text: `Response clock: ${space.responseClockHours} hours · Pleas ${space.pleasEnabled ? 'allowed' : 'off'}` }),
            ),
        space.threadId && h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/yarnspaces.html#t/${space.threadId}`, text: 'Open the room' }),
        h('section', { class: 'spc-card sp-glass' },
            h('h2', { text: `The team · ${members.length}` }),
            h('div', { class: 'spc-members' }, ...members.map(memberRow)),
            space.role === 'MEMBER' && !space.canManage && h('div', { class: 'pc-actions' }, h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Leave space',
                onclick: () => act(() => spaceLeave(space.id), 'You left the space.') }))),
        milestonesSection(space, { canLog: can('LOG_MILESTONES'), me }),
        inRoom && paymentsSection(space, { canLog: can('LOG_PAYMENTS'), me, members }),
        inRoom && removalsSection(space, { me })].filter(Boolean));
}

const YARNS = '/HTML-pages/yarnspaces.html';

/** The same bar as Yarns, with this space's settings in place of the Yarns ones. */
function mountBar() {
    const mayEdit = () => !!members?.find((m) => m.person.username === me.username)?.permissions.includes('EDIT_SETTINGS');
    return mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, holdActions: fanActions(), visible: 3, activeIndex: 4, restIcon: svg(PEOPLE),
        links: ['All yarns', 'MySpaces', 'WeSpaces', 'WorkSpaces', 'Settings'],
        hrefs: [YARNS, `${YARNS}#myspace`, `${YARNS}#wespace`, `${YARNS}#workspace`, '#'],
        icons: [CHAT, PERSON, PEOPLE, BRIEFCASE, GEAR].map(svg),
        onChange: (label, href) => {
            if (label !== 'Settings') { location.href = href.slice(href.indexOf('/HTML-pages')); return; }
            if (space) { if (mayEdit()) openSettings(space, refresh); else openThemeSettings(); }
        },
    });
}

async function boot() {
    me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    mountBar();
    try { await refresh(); }
    catch (err) {
        if (err.status === 401) return toLogin();
        say(err.status === 404 ? 'This space does not exist, or it is not open to you.' : err.message);
    }
}

mountThemeSwitcher('#theme-slot', { inline: true, collapse: true });
boot();
