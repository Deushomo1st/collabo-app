// The bar on a settings page: just the gear. Hold it and the one button that fans out takes you back to where you came from.
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { GEAR, RETURN, svg } from '/js/services/icons.js';

export function mountSettingsNav(fallback) {
    return mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 2500, activeIndex: 0,
        links: ['Settings'], hrefs: ['#'], icons: [svg(GEAR)],
        holdActions: [{ label: 'Return', icon: svg(RETURN), onSelect: () => { if (history.length > 1) history.back(); else location.href = fallback; } }],
    });
}
