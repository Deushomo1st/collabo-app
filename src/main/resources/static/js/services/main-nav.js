// The main bottom bar: Gaze, Yarns, Post, Profile. It follows you between those pages; `active` says which one you are on.
// Pages with their own bar (Yarns, the space page, post, report) keep it. On your own profile the last item is Settings (the gear).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { fanActions } from '/js/services/fan-actions.js';
import { profileHref } from '/js/services/dom.js';
import { GEAR, HOME, CHAT, PERSON, svg } from '/js/services/icons.js';

const GAZE = '/HTML-pages/gaze.html';

/** onGaze: what tapping Gaze does when you are already on it (scroll up); elsewhere it goes to the Gaze. */
export async function mountMainNav(username, active, { onGaze, onSettings } = {}) {
    const pages = [
        ['Gaze', GAZE, svg(HOME)],
        ['Yarns', '/HTML-pages/yarnspaces.html', svg(CHAT)],
        ['Post', '/HTML-pages/post.html', svg('<path d="M12 5v14M5 12h14"/>')],
        onSettings   // on your own profile the last item is its settings
            ? ['Settings', '#', svg(GEAR)]
            : ['Profile', profileHref(username), svg('<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>')],
    ];
    const here = pages.findIndex((p) => p[0] === active);
    const nav = await mountNavSelector('#bottom-nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, activeIndex: onSettings ? -1 : here, holdActions: fanActions(),
        restIcon: onSettings ? svg(PERSON) : undefined,   // your own profile rests on the person, not the gear
        links: pages.map((p) => p[0]), hrefs: pages.map((p) => p[1]),
        icons: pages.map((p) => p[2]),
        onChange: (label, href) => {
            if (label === active) { if (label === 'Gaze' && onGaze) onGaze(); if (label === 'Settings' && onSettings) onSettings(); return; }
            if (label === 'Post') nav.setActive(here);   // the post page is a page of its own, so the bar keeps showing where you are
            location.href = href;
        },
    });
    return nav;
}

/**
 * Gaze, Yarns, Settings: the bar on the post and report pages. Settings opens the page's own settings page.
 * beforeLeave(href) may take over leaving (to ask about unsaved work); beforeSettings() runs first on the way to the settings page.
 */
export async function mountComposeNav(settingsHref, { beforeLeave, beforeSettings, restInner = GEAR } = {}) {
    const to = { Gaze: GAZE, Yarns: '/HTML-pages/yarnspaces.html' };
    const nav = await mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, activeIndex: 2, holdActions: fanActions(), restIcon: svg(restInner),
        links: ['Gaze', 'Yarns', 'Settings'], hrefs: [GAZE, to.Yarns, settingsHref],
        icons: [svg(HOME), svg(CHAT), svg(GEAR)],
        onChange: (label) => {
            if (label === 'Settings') { beforeSettings?.(); location.href = settingsHref; return; }
            nav.setActive(2);   // this page is still the one you are on until you actually leave
            if (beforeLeave) beforeLeave(to[label]); else location.href = to[label];
        },
    });
    return nav;
}
