// A WeSpace's "about the group", reached as wespace.html?post=<post id> from the room's header bar.
// Three tabs: About (the idea and the way in), Collaborators (the seats), Quiet (the response clock and flagged collaborators).
// Each tab is built by its own module under components/wespace. Text goes in through textContent only.
import '/js/services/live.js';
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { fanActions } from '/js/services/fan-actions.js';
import { CHAT, PERSON, PEOPLE, BRIEFCASE, svg } from '/js/services/icons.js';
import { currentUser, wespaceAbout } from '/js/services/api.js';
import { h } from '/js/services/dom.js';
import { seatsSection } from '/js/components/wespace/seats.js';
import { clockRow } from '/js/components/wespace/clock.js';
import { casesSection } from '/js/components/wespace/cases.js';

const YARNS = '/HTML-pages/yarnspaces.html';
const ROLE = { FOUNDER: 'Founder', COLLABORATOR: 'Collaborator', FROZEN: 'Frozen' };

const postId = new URLSearchParams(location.search).get('post');
const main = () => document.getElementById('space');
let about, me, tab = 'about';

async function refresh() {
    about = await wespaceAbout(postId);
    draw();
}

/** The idea, plus the doors out to the room, the applicants and the Workspace. */
function aboutTab() {
    return [
        h('section', { class: 'spc-card sp-glass' },
            h('h2', { text: about.title }),
            h('p', { class: 'pc-hint', text: `Idea status: ${about.postStatus}` }),
            h('p', { class: 'spc-idea', text: about.body })),
        about.threadId && h('a', { class: 'sp-btn sp-btn--brand', href: `${YARNS}#t/${about.threadId}`, text: 'Open the room' }),
        about.role === 'FOUNDER' && h('a', { class: 'sp-btn', href: `/HTML-pages/applicants.html?post=${postId}`, text: 'Review applicants' }),
        about.spaceId && h('a', { class: 'sp-btn', href: `/HTML-pages/space.html?id=${about.spaceId}`, text: 'Open the Workspace' }),
    ];
}

/** A frozen collaborator can read the seats but has no part in the clock or the cases. */
function sections() {
    const all = [['about', 'About', aboutTab], ['seats', 'Collaborators', () => [seatsSection(about, { me, refresh })]]];
    if (about.role !== 'FROZEN') all.push(['quiet', 'Quiet', () => [clockRow(about, refresh), casesSection(about, { me, onDecided: refresh })]]);
    return all;
}

function draw() {
    document.title = `COLLABO — ${about.title}`;
    document.getElementById('space-name').textContent = about.title;
    const role = document.getElementById('space-role');
    role.textContent = ROLE[about.role]; role.hidden = false;
    const all = sections();
    if (!all.some(([k]) => k === tab)) tab = 'about';
    const bar = h('nav', { class: 'sp-tt', role: 'tablist' }, ...all.map(([k, label]) =>
        h('button', { class: k === tab ? 'is-on' : '', type: 'button', role: 'tab', 'aria-selected': String(k === tab), text: label, onclick: () => { tab = k; draw(); } })));
    main().replaceChildren(bar, ...all.find(([k]) => k === tab)[2]().filter(Boolean));   // only the open tab is built
}

async function boot() {
    me = await currentUser().catch(() => null);
    if (!me) return location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
    mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 0, holdActions: fanActions(), activeIndex: 2, restIcon: svg(PEOPLE),
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
