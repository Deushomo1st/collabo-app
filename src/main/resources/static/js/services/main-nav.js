// The main bottom bar: Gaze, Yarns, Post, Profile. It follows you between those pages; `active` says which one you are on.
// Pages with their own bar (Yarns, the space page) keep it; the post and report pages stay bare (just the way back).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { fanActions } from '/js/services/fan-actions.js';
import { profileHref } from '/js/services/dom.js';

const svg = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const GAZE = '/HTML-pages/gaze.html';

/** onGaze: what tapping Gaze does when you are already on it (scroll up); elsewhere it goes to the Gaze. */
export async function mountMainNav(username, active, { onGaze } = {}) {
    const pages = [
        ['Gaze', GAZE, svg('<path d="M3 10.5 12 3l9 7.5V21H3z"/>')],
        ['Yarns', '/HTML-pages/yarnspaces.html', svg('<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>')],
        ['Post', '/HTML-pages/post.html', svg('<path d="M12 5v14M5 12h14"/>')],
        ['Profile', profileHref(username), svg('<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>')],
    ];
    const here = pages.findIndex((p) => p[0] === active);
    const nav = await mountNavSelector('#bottom-nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, activeIndex: here, holdActions: fanActions(),
        links: pages.map((p) => p[0]), hrefs: pages.map((p) => p[1]),
        icons: pages.map((p) => p[2]),
        onChange: (label, href) => {
            if (label === active) { if (label === 'Gaze' && onGaze) onGaze(); return; }
            if (label === 'Post') nav.setActive(here);   // the post page is a page of its own, so the bar keeps showing where you are
            location.href = href;
        },
    });
    return nav;
}
