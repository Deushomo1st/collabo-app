// The five buttons that fan out when the collapsed bottom nav is held, the same on every page, left to right: Home, Report, New post (top centre), Back, Profile.
// Used as `mountNavSelector(el, { holdActions: fanActions() })`. Report is left out on the report page itself.
// Pages with unsaved work pass guard(go): it runs go() to leave, or asks first (the post and report pages).
const icon = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;
const GAZE = '/HTML-pages/gaze.html';
const POST = '/HTML-pages/create-post.html';
const PROFILE = '/HTML-pages/profile.html';   // no ?u= means your own
const REPORT = '/HTML-pages/report.html';

export function fanActions(guard = (go) => go()) {
    const to = (href) => () => guard(() => { location.href = href; });
    return [
        { label: 'Home', icon: icon('<path d="M3 10.5 12 3l9 7.5V21H3z"/><path d="M9 21v-7h6v7"/>'),
            onSelect: () => { if (location.pathname === GAZE) window.scrollTo({ top: 0, behavior: 'smooth' }); else guard(() => { location.href = GAZE; }); } },
        { label: 'Report a problem', icon: icon('<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>'),
            onSelect: to(`${REPORT}?from=${encodeURIComponent(location.pathname + location.search + location.hash)}`) },
        { label: 'New post', icon: icon('<path d="M12 5v14M5 12h14"/>'),
            onSelect: to(POST) },
        { label: 'Back', icon: icon('<path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>'),
            onSelect: () => guard(() => { if (history.length > 1) history.back(); else location.href = GAZE; }) },
        { label: 'Profile', icon: icon('<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>'),
            onSelect: to(PROFILE) },
    ].filter((a) => !(a.label === 'Report a problem' && location.pathname === REPORT));
}
