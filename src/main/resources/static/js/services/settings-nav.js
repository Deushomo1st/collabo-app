// The bar on a settings page: just the gear. Hold it and three buttons fan out: Home (left), Report a problem (centre), Return (right).
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import '/js/services/nav-mode.js';
import { GEAR, RETURN, svg } from '/js/services/icons.js';
import { fanActions } from '/js/services/fan-actions.js';

export function mountSettingsNav(fallback) {
    return mountNavSelector('#nav', {
        placement: 'bottom', collapseWhenIdle: true, idleMs: 0, activeIndex: 0,
        links: ['Settings'], hrefs: ['#'], icons: [svg(GEAR)],
        holdActions: [...fanActions().slice(0, 2), { label: 'Return', icon: svg(RETURN), onSelect: () => { if (history.length > 1) history.back(); else location.href = fallback; } }],
    });
}
