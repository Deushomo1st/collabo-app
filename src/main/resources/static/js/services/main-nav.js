// The one bottom bar, the same on every page: Gaze, Yarns, Profile, Settings, Help. `active` says which one you are on (null: none, the page shows its own icon).
// Pages with unsaved work (post, report) pass beforeLeave(href) / guard(go) so leaving asks first. Hold the collapsed button for the fan (fan-actions.js).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { leave } from '/js/services/nav-mode.js';
import { fanActions } from '/js/services/fan-actions.js';
import { DEST, activeFor, restFor } from '/js/services/destinations.js';
import { GRID, svg } from '/js/services/icons.js';

const BAR = ['Gaze', 'Yarns', 'Profile', 'Settings', 'Help'];

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
 * Which one is lit and what the collapsed button rests on both follow from the page (destinations.js); a page only says what is its own.
 * onGaze: what tapping Gaze does when you are already on it (scroll up). restIcon: an svg string or a function making one, for a page that rests on a picture of its own
 * (your profile rests on your picture). Pages with unsaved work say so once, with guardReturn (nav-mode.js), and the bar, the fan and the menu all ask first.
 */
export async function mountMainNav(username, { onGaze, restIcon } = {}) {
    const active = activeFor(username), here = BAR.indexOf(active);
    const rest = restFor() ?? (here < 0 ? GRID : null);
    const hrefs = BAR.map((k) => DEST[k].href(username));   // Help opens the cards for this page (help.js) and leaves nothing
    speculate(hrefs);
    const nav = await mountNavSelector(document.querySelector('#bottom-nav, #nav'), {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 0, activeIndex: here, holdActions: fanActions(username), restIcon: restIcon ?? (rest && svg(rest)),   // a page with no icon of its own (someone else's profile) rests on the four squares
        links: BAR, hrefs,
        icons: BAR.map((k) => svg(DEST[k].icon)),
        onChange: (label, href) => {
            if (label === 'Help') return import('/js/services/help.js').then((m) => m.openHelp(() => nav.setActive(here)));   // back to the page's own (or none)
            if (label === active) { if (label === 'Gaze') onGaze?.(); return; }
            leave(() => setTimeout(() => { location.href = href; }, 250));   // let the bubble slide to the pick before the page changes
        },
    });
    // coming back (bfcache) the bar shows the page you are on again, not the link you left by
    addEventListener('pageshow', (e) => { if (e.persisted) nav.setActive(here); });
    return nav;
}
