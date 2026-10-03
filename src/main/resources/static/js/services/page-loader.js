// A loading screen for a move to another page that takes a moment: if the next page has not come back within 350 ms the current one is covered by a spinner,
// so the tap never looks ignored. Quick moves never show it. A tap on it puts it away (the move carries on regardless).
import { h } from '/js/services/dom.js';

const DELAY = 350;
let timer = null, veil = null;
const hide = () => { clearTimeout(timer); veil?.remove(); veil = null; };
const show = () => {
    if (veil) return;
    veil = h('div', { class: 'page-veil', role: 'status', 'aria-label': 'Loading the next page' }, h('span', { class: 'page-veil__ring' }), h('span', { class: 'page-veil__text', text: 'Loading…' }));
    veil.addEventListener('pointerdown', hide);
    document.body.append(veil);
    setTimeout(hide, 15000);   // never stuck, whatever happens
};
addEventListener('beforeunload', () => { clearTimeout(timer); timer = setTimeout(show, DELAY); });   // any move away: a link, the bar, the menu, a reload
addEventListener('pageshow', hide);   // coming back from the cache
