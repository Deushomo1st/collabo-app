// "Navigation preference" (a sub-modal of Settings > Appearance and themes): a row of glowing flat tiles that scrolls sideways, one per entry in
// nav-preference-source.js. The pick is saved on the account (nav-mode.js), so it follows you to every device.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { h } from '/js/services/dom.js';
import { NAV_PREFERENCES } from '/js/services/nav-preference-source.js';
import { navMode, saveNavPreference } from '/js/services/nav-mode.js';

const saved = () => NAV_PREFERENCES.find((p) => p.id === navMode());

/** The saved preference's id: the account's pick, else the default. */
export const navPreference = () => saved()?.id ?? null;

/** The line under "Navigation preference" in Settings. */
export const navPreferenceName = () => saved()?.name ?? 'Not set';

export async function openNavPreference(onClose) {
    const { panel } = await openGlassBlurDialog({
        size: 'sm', label: 'Navigation preference', onClose,
        html: '<h3 class="glass-blur-dialog__title">Navigation preference</h3><div></div>',
    });
    const body = panel.lastElementChild;
    if (!NAV_PREFERENCES.length) return body.append(h('p', { class: 'np-hint', text: 'No navigation preferences yet.' }));
    let on = navPreference();
    const tiles = NAV_PREFERENCES.map((p) => h('button', {
        class: 'np-tile', type: 'button', role: 'radio', 'aria-checked': String(p.id === on), 'data-id': p.id,
    }, p.image && h('span', { class: 'np-pic' }, h('img', { class: 'np-pic--dusk', src: p.image.dusk, alt: '', draggable: 'false' }), h('img', { class: 'np-pic--light', src: p.image.light, alt: '', draggable: 'false' })),
    h('span', { class: 'np-name' }, h('strong', { text: p.name }), p.note ? h('span', { text: p.note }) : null)));
    const grid = h('div', { class: 'np-grid', role: 'radiogroup', 'aria-label': 'Navigation preference' }, tiles);
    const hint = h('p', { class: 'np-hint', text: 'Saved on your account, so every device uses it.' });
    grid.addEventListener('click', (e) => {
        const t = e.target.closest('.np-tile'); if (!t) return;
        on = t.dataset.id;
        tiles.forEach((x) => x.setAttribute('aria-checked', String(x === t)));
        saveNavPreference(on).then(() => { hint.textContent = 'Saved on your account, so every device uses it.'; },
            () => { hint.textContent = 'Could not save to your account. It is kept on this device for now.'; });
    });
    body.append(grid, hint);
}
