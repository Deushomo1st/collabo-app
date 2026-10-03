// The places the app leads to, written once: where each goes, its icon, and which page files count as "being on it" (the bottom bar lights it).
// The bar (main-nav.js), the fan (fan-actions.js) and the four-square menu (nav-menu.js) all read this, so a destination changes in one place.
import { profileHref } from '/js/services/dom.js';
import { GEAR, HOME, CHAT, BELL, PERSON, PEOPLE, QUESTION } from '/js/services/icons.js';

export const PLUS = '<path d="M12 5v14M5 12h14"/>';
export const FLAG = '<path d="M4 22V4h12l-2 4 2 4H4"/>';
const file = () => location.pathname.split('/').pop().replace(/\.html$/, '');

/** href(me): where it leads. on: the page files that sit under it. */
export const DEST = {
    Gaze: { href: () => '/HTML-pages/gaze.html', icon: HOME, on: ['gaze', 'view-post', 'applicants', 'notifications'] },
    Yarns: { href: () => '/HTML-pages/yarnspaces.html', icon: CHAT, on: ['yarnspaces', 'space', 'wespace', 'workspace'] },
    Notifications: { href: () => '/HTML-pages/notifications.html', icon: BELL, on: [] },
    Post: { href: () => '/HTML-pages/create-post.html', icon: PLUS, on: [] },
    Profile: { href: (me) => (me ? profileHref(me) : '/HTML-pages/profile.html'), icon: PERSON, on: ['profile', 'applications', 'connections'] },   // no ?u= is your own
    Settings: { href: () => '/HTML-pages/profile-settings.html', icon: GEAR, on: ['profile-settings', 'history'] },
    Report: { href: () => '/HTML-pages/report.html', icon: FLAG, on: [] },
    Help: { href: () => '#', icon: QUESTION, on: [] },   // a dialog on the page you are on, nothing to leave
};

/** The icon a page rests on in the collapsed button when none of the bar's five is its own. */
export const REST = { space: PEOPLE, wespace: PEOPLE, 'create-post': PLUS, report: FLAG };

/** Which of the bar's destinations this page is under (null for none). Other people's profiles are not yours. */
export function activeFor(me) {
    const f = file(), u = new URLSearchParams(location.search).get('u');
    if (f === 'profile' && u && u !== me) return null;
    return Object.keys(DEST).find((k) => DEST[k].on.includes(f)) ?? null;
}
export const restFor = () => REST[file()] ?? null;
