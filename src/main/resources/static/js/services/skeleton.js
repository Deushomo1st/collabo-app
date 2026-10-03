// Grey placeholder shapes shown while a list loads, so the page has its shape at once instead of empty space.
// They pulse with opacity only (cheap), and are hidden from screen readers.
import { h } from '/js/services/dom.js';

const lines = () => [h('div', { class: 'sk-line sk-w40' }), h('div', { class: 'sk-line' }), h('div', { class: 'sk-line sk-w70' })];

/** n card-shaped placeholders (a feed). */
export const skeletonCards = (n = 3) => Array.from({ length: n }, () => h('div', { class: 'sk-card', 'aria-hidden': 'true' }, ...lines()));

/** n row-shaped placeholders (a list of people). */
export const skeletonRows = (n = 5) => Array.from({ length: n }, () => h('div', { class: 'sk-row', 'aria-hidden': 'true' },
    h('span', { class: 'sk-dot' }), h('div', { class: 'sk-lines' }, h('div', { class: 'sk-line sk-w40' }), h('div', { class: 'sk-line sk-w70' }))));

/** A chat before its yarns arrive: bubbles on alternating sides. */
export const skeletonBubbles = () => [[60, 0], [40, 1], [70, 0], [50, 1]].map(([w, mine]) => h('div', { class: `sk-bubble${mine ? ' sk-bubble--mine' : ''}`, style: `width:${w}%`, 'aria-hidden': 'true' }));

/** A profile's identity card before it arrives: the picture, the name, the handle. */
export const skeletonProfile = () => h('div', { class: 'sk-profile', 'aria-hidden': 'true' }, h('span', { class: 'sk-dot sk-dot--big' }), h('div', { class: 'sk-line sk-w40' }), h('div', { class: 'sk-line sk-w40' }));
