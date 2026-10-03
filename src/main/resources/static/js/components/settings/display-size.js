// Settings > Appearance and themes > "Display size": a slider for how big text, icons and spacing are, with a small sample screen under it
// that grows and shrinks as you slide. Letting go keeps the size and applies it to the whole app (ui-scale.js).
import { h } from '/js/services/dom.js';
import { svg, HOME, CHAT, PERSON, GEAR } from '/js/services/icons.js';
import { SCALE, uiScale, saveUiScale } from '/js/services/ui-scale.js';
import { profileUpdate } from '/js/services/api.js';

const pct = (v) => `${Math.round(v * 100)}%`;
const icon = (inner) => { const s = h('span', { class: 'ds-ico' }); s.innerHTML = svg(inner); return s; };   // fixed markup, never user text

function sample() {
    return h('div', { class: 'ds-screen', 'aria-hidden': 'true' },
        h('div', { class: 'ds-zoom' },
            h('div', { class: 'ds-top' }, h('strong', { text: 'Gaze' }), icon(GEAR)),
            h('div', { class: 'ds-card' },
                h('div', { class: 'ds-who' }, h('i', { class: 'ds-face', text: 'A' }), h('span', {}, h('b', { text: 'Ada' }), h('small', { text: '2h ago' }))),
                h('p', { text: 'Looking for a designer to build the first screens of a savings app.' }),
                h('div', { class: 'ds-actions' }, h('span', { class: 'ds-btn', text: 'Apply' }), h('span', { class: 'ds-btn ds-btn--ghost', text: 'Comment' }))),
            h('div', { class: 'ds-pill' }, icon(HOME), icon(CHAT), icon(PERSON), icon(GEAR))));
}

/** The row to drop into a Settings card. */
export function displaySizeRow() {
    let now = uiScale();
    const shown = h('strong', { class: 'ds-value', text: pct(now) });
    const zoomer = sample();
    const inner = zoomer.firstElementChild;
    const preview = (v) => { inner.style.zoom = String(v / now); shown.textContent = pct(v); };   // the page is already at `now`; the sample shows the difference
    const slider = h('input', { class: 'ds-range', type: 'range', min: String(SCALE.min * 100), max: String(SCALE.max * 100), step: String(SCALE.step * 100), value: String(now * 100), 'aria-label': 'Display size' });
    slider.addEventListener('input', () => preview(slider.value / 100));
    slider.addEventListener('change', () => { saveUiScale(slider.value / 100); profileUpdate({ displaySize: Math.round(slider.value) }).catch(() => {}); now = slider.value / 100; inner.style.zoom = '1'; requestAnimationFrame(() => slider.scrollIntoView({ block: 'center' })); });   // kept, and the whole page follows; the slider stays mid-screen, clear of the round button
    const reset = h('button', { class: 'ds-reset', type: 'button', text: 'Reset to 100%', onclick: () => { slider.value = '100'; slider.dispatchEvent(new Event('change')); shown.textContent = '100%'; } });
    return h('div', { class: 'ds-row' },
        h('div', { class: 'ds-head' }, h('strong', { text: 'Display size' }), shown),
        h('span', { class: 'yn-last', text: 'How big text and icons are on this device. Slide to see it, let go to keep it.' }),
        h('div', { class: 'ds-slide' }, h('small', { text: 'A' }), slider, h('big', { text: 'A' })),
        zoomer, reset);
}
