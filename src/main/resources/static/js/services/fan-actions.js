// The three buttons that fan out when the collapsed bottom nav is held, the same on every page: Home, Report, Back.
// Used as `mountNavSelector(el, { holdActions: fanActions() })`.
const icon = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;
const GAZE = '/HTML-pages/gaze.html';

export function fanActions() {
    return [
        { label: 'Home', icon: icon('<path d="M3 10.5 12 3l9 7.5V21H3z"/><path d="M9 21v-7h6v7"/>'),
            onSelect: () => { if (location.pathname === GAZE) window.scrollTo({ top: 0, behavior: 'smooth' }); else location.href = GAZE; } },
        { label: 'Report a problem', icon: icon('<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>'),
            onSelect: () => { location.href = `/HTML-pages/report.html?from=${encodeURIComponent(location.pathname + location.search + location.hash)}`; } },
        { label: 'Back', icon: icon('<path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>'),
            onSelect: () => { if (history.length > 1) history.back(); else location.href = GAZE; } },
    ];
}
