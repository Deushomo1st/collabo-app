// Settings > Appearance and themes > "Fan spread": how tightly the buttons fan out when you hold the round button, with a small fan that follows
// the slider. Letting go keeps it (hold-fan.js reads it at every opening) and saves it on the account. Only shown with the round button (nav-preference.css).
import { h } from '/js/services/dom.js';
import { TIGHTNESS, fanTightness, saveFanTightness, fanPoints } from '/js/components/hold-fan/hold-fan.js';
import { profileUpdate } from '/js/services/api.js';

const SCALE = 0.45;   // the sample fan, drawn smaller than the real one

/** The row to drop into a Settings card. */
export function fanSpreadRow() {
    const shown = h('strong', { class: 'ds-value' });
    const dots = fanPoints(5).map(() => h('i', { class: 'fs-dot' }));
    const draw = (t) => {
        fanPoints(5, t).forEach((p, i) => { dots[i].style.transform = `translate(${p.x * SCALE}px, ${p.y * SCALE}px)`; });
        shown.textContent = `${t}%`;
    };
    const slider = h('input', { class: 'ds-range', type: 'range', min: String(TIGHTNESS.min), max: String(TIGHTNESS.max), step: String(TIGHTNESS.step), value: String(fanTightness()), 'aria-label': 'Fan spread tightness' });
    slider.addEventListener('input', () => draw(Number(slider.value)));
    slider.addEventListener('change', () => { saveFanTightness(Number(slider.value)); profileUpdate({ fanTightness: Number(slider.value) }).catch(() => {}); });
    const reset = h('button', { class: 'ds-reset', type: 'button', text: 'Reset to 100%', onclick: () => { slider.value = '100'; draw(100); slider.dispatchEvent(new Event('change')); } });
    draw(fanTightness());
    return h('div', { class: 'ds-row fs-row' },
        h('div', { class: 'ds-head' }, h('strong', { text: 'Fan spread' }), shown),
        h('span', { class: 'yn-last', text: 'How tightly the buttons fan out when you hold the round button. Higher pulls them in closer.' }),
        h('div', { class: 'ds-slide' }, h('small', { text: 'Loose' }), slider, h('small', { text: 'Tight' })),
        h('div', { class: 'fs-preview', 'aria-hidden': 'true' }, ...dots, h('i', { class: 'fs-dot fs-dot--home' })),
        reset);
}
