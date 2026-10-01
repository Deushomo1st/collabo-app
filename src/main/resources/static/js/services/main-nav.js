// The main bottom bar: Gaze, Yarns, Post, Profile. It follows you between those pages; `active` says which one you are on.
// Pages with their own bar (Yarns, the space page, post, report) keep it. On your own profile the last item is Settings (the gear).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { fanActions } from '/js/services/fan-actions.js';
import { profileHref } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { GEAR, HOME, CHAT, PERSON, svg } from '/js/services/icons.js';

const GAZE = '/HTML-pages/gaze.html';

/** Asks the browser to load the pages the bar leads to in the background (on hover or touch), so the tap opens them at once. */
function speculate(hrefs) {
    if (!HTMLScriptElement.supports?.('speculationrules')) return;
    const here = location.pathname + location.search;
    const urls = [...new Set(hrefs.filter((h) => h && h !== '#').map((h) => { const u = new URL(h, location.href); return u.pathname + u.search; }))].filter((u) => u !== here);
    if (!urls.length) return;
    const s = document.createElement('script');
    s.type = 'speculationrules';
    s.textContent = JSON.stringify({ prerender: [{ source: 'list', urls, eagerness: 'moderate' }] });
    document.head.append(s);
}

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
    speculate(pages.map((p) => p[1]));
    const nav = await mountNavSelector('#bottom-nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, visible: 3, activeIndex: onSettings ? -1 : here, holdActions: fanActions(),
        restIcon: onSettings ? () => face(username, 'ns-face') : undefined,   // your own profile rests on your picture (your initial if none), not the gear
        links: pages.map((p) => p[0]), hrefs: pages.map((p) => p[1]),
        icons: pages.map((p) => p[2]),
        onChange: (label, href) => {
            if (label === active) { if (label === 'Gaze' && onGaze) onGaze(); if (label === 'Settings' && onSettings) onSettings(); return; }
            setTimeout(() => { location.href = href; }, 250);   // let the bubble slide to the pick before the page changes
        },
    });
    // coming back (bfcache) the bar shows the page you are on again, not the link you left by
    addEventListener('pageshow', (e) => { if (e.persisted && here >= 0) nav.setActive(here); });
    return nav;
}

/**
 * Gaze, Yarns, Settings: the bar on the post and report pages. Settings opens the page's own settings page.
 * beforeLeave(href) may take over leaving (to ask about unsaved work); beforeSettings() runs first on the way to the settings page.
 */
export async function mountComposeNav(settingsHref, { beforeLeave, beforeSettings, restInner = GEAR } = {}) {
    const to = { Gaze: GAZE, Yarns: '/HTML-pages/yarnspaces.html' };
    speculate([GAZE, to.Yarns]);
    const nav = await mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 5000, visible: 3, activeIndex: 2, holdActions: fanActions(), restIcon: svg(restInner),
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
