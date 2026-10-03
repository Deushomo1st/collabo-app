// The five buttons that fan out when the collapsed bottom nav is held, the same on every page, left to right: Home, Report, New post (top centre), Back, Profile.
// Used as `mountNavSelector(el, { holdActions: fanActions(username) })`. The one for the page you are on is left out, and Help takes the centre in its place.
// Leaving goes through nav-mode.js's guard, so a page with unsaved work (post, report) asks first. Where each goes and its icon come from destinations.js.
import { leave } from '/js/services/nav-mode.js';
import { DEST } from '/js/services/destinations.js';
import { RETURN, svg } from '/js/services/icons.js';

const GAZE = DEST.Gaze.href(), POST = DEST.Post.href(), PROFILE = DEST.Profile.href(), REPORT = DEST.Report.href();   // Profile with no ?u= is your own
const icon = (k) => svg(DEST[k].icon);

/** Which of the five is this page: Home on the Gaze, Report, New post, Profile on your own (no ?u=, or yours). */
function here(me) {
    const p = location.pathname, u = new URLSearchParams(location.search).get('u');
    return p === GAZE ? 'Home' : p === REPORT ? 'Report a problem' : p === POST ? 'New post' : p === PROFILE && (!u || u === me) ? 'Profile' : null;
}

export function fanActions(me = null) {
    const to = (href) => () => leave(() => { location.href = href; });
    const all = [
        { label: 'Home', icon: icon('Gaze'),
            onSelect: () => { if (location.pathname === GAZE) window.scrollTo({ top: 0, behavior: 'smooth' }); else leave(() => { location.href = GAZE; }); } },
        { label: 'Report a problem', icon: icon('Report'),
            onSelect: to(`${REPORT}?from=${encodeURIComponent(location.pathname + location.search + location.hash)}`) },
        { label: 'New post', icon: icon('Post'),
            onSelect: to(POST) },
        { label: 'Back', icon: svg(RETURN),
            onSelect: () => leave(() => { if (history.length > 1) history.back(); else location.href = GAZE; }) },
        { label: 'Profile', icon: icon('Profile'),
            onSelect: to(PROFILE) },
    ];
    const left = all.filter((a) => a.label !== here(me));
    if (left.length === all.length) return all;
    left.splice(2, 0, { label: 'Help', icon: icon('Help'), onSelect: () => import('/js/services/help.js').then((m) => m.openHelp()) });   // the freed place, in the centre
    return left;
}
