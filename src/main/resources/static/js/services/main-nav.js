// The one bottom bar, the same on every page: Gaze, Yarns, Profile, Settings, Help. `active` says which one you are on (null: none, the page shows its own icon).
// Pages with unsaved work (post, report) pass beforeLeave(href) / guard(go) so leaving asks first. Hold the collapsed button for the fan (fan-actions.js).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import '/js/services/nav-mode.js';
import { fanActions } from '/js/services/fan-actions.js';
import { profileHref } from '/js/services/dom.js';
import { GEAR, HOME, CHAT, PERSON, svg } from '/js/services/icons.js';

const GAZE = '/HTML-pages/gaze.html';
const HELP = '/HTML-pages/how.html';   // ponytail: Help opens the how-it-works page until the guided help screens exist
const QUESTION = '<circle cx="12" cy="12" r="10"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3"/><path d="M12 17h.01"/>';

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

/**
 * onGaze: what tapping Gaze does when you are already on it (scroll up). restIcon: an svg string or a function making one, for pages that rest on their own icon
 * (your profile rests on your picture, post and report on theirs).
 */
export async function mountMainNav(username, active, { onGaze, beforeLeave, guard, restIcon } = {}) {
    const to = { Gaze: GAZE, Yarns: '/HTML-pages/yarnspaces.html', Profile: profileHref(username), Settings: '/HTML-pages/profile-settings.html', Help: HELP };
    const labels = Object.keys(to), here = labels.indexOf(active);
    speculate(Object.values(to));
    const nav = await mountNavSelector(document.querySelector('#bottom-nav, #nav'), {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 0, activeIndex: here, holdActions: fanActions(guard), restIcon,
        links: labels, hrefs: Object.values(to),
        icons: [HOME, CHAT, PERSON, GEAR, QUESTION].map(svg),
        onChange: (label, href) => {
            if (label === active) { if (label === 'Gaze') onGaze?.(); return; }
            if (beforeLeave) beforeLeave(href); else setTimeout(() => { location.href = href; }, 250);   // let the bubble slide to the pick before the page changes
        },
    });
    // coming back (bfcache) the bar shows the page you are on again, not the link you left by
    addEventListener('pageshow', (e) => { if (e.persisted && here >= 0) nav.setActive(here); });
    return nav;
}
