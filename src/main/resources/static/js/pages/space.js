// A space: the idea it grew from, joining (for accepted applicants), and the people in it.
// All network calls live in js/services/api.js; text goes in through textContent only.
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { currentUser, spaceById, spaceOfPost, spaceJoin, spaceLeave, spaceMembers } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const ROLE = { OWNER: 'Owner', MEMBER: 'Member', APPLICANT: 'Accepted' };
const PERM = { LOG_MILESTONES: 'log milestones', LOG_PAYMENTS: 'log payments', ACCEPT_MEMBERS: 'accept and remove members',
    EDIT_SETTINGS: 'edit settings', POST_IN_ROOM: 'post and pin', MANAGE_RECRUITMENT: 'manage recruitment' };

const main = () => document.getElementById('space');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
const say = (text) => main().replaceChildren(h('p', { class: 'gz-empty', text }));

let space, members;

async function refresh() {
    const params = new URLSearchParams(location.search);
    space = params.get('id') ? await spaceById(params.get('id')) : await spaceOfPost(params.get('post'));
    members = await spaceMembers(space.id);   // accepted applicants may read the list too
    draw();
}

async function act(fn, done) {
    try { await fn(); if (done) toast(done); await refresh(); } catch (err) { toast(err.message); }
}

function memberRow(m) {
    return h('div', { class: 'spc-member' },
        h('a', { class: 'pc-who', href: profileHref(m.person.username), text: m.person.username }),
        m.person.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: m.person.preferredTitle }),
        m.title && h('span', { class: `sp-tag ${m.owner ? 'sp-tag--ok' : 'sp-tag--muted'}`, text: m.title }),
        !m.owner && m.permissions.length > 0 && h('span', { class: 'spc-perms', text: m.permissions.map((p) => PERM[p] || p).join(' · ') }));
}

function draw() {
    document.getElementById('space-name').textContent = space.name;
    const tag = document.getElementById('space-role');
    tag.textContent = ROLE[space.role] || space.role; tag.hidden = false;
    document.title = `COLLABO — ${space.name}`;
    main().replaceChildren(...[
        space.role === 'APPLICANT' && h('section', { class: 'spc-card spc-join sp-glass' },
            h('h2', { text: 'You were accepted' }),
            h('p', { class: 'pc-hint', text: 'You can read everything here. Joining is your call: it puts you on the team and in the room.' }),
            h('div', { class: 'pc-actions' }, h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Join space',
                onclick: () => act(() => spaceJoin(space.id), 'You joined the space.') }))),
        h('section', { class: 'spc-card sp-glass' },
            h('h2', { text: space.postTitle }),
            h('p', { class: 'pc-hint' }, 'The idea, by ', h('a', { class: 'pc-who', href: profileHref(space.owner.username), text: space.owner.username })),
            h('p', { class: 'spc-idea', text: space.postBody })),
        h('section', { class: 'spc-card sp-glass' },
            h('h2', { text: `The team · ${members.length}` }),
            h('div', { class: 'spc-members' }, ...members.map(memberRow)),
            space.role === 'MEMBER' && h('div', { class: 'pc-actions' }, h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Leave space',
                onclick: () => act(() => spaceLeave(space.id), 'You left the space.') })))].filter(Boolean));
}

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    try { await refresh(); }
    catch (err) {
        if (err.status === 401) return toLogin();
        say(err.status === 404 ? 'This space does not exist, or it is not open to you.' : err.message);
    }
}

mountThemeSwitcher('#theme-slot', { inline: true });
boot();
