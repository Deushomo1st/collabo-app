// The Help on the round button and in the four-square menu: cards for the page you are on, in a dialog. Nothing is left and nobody is signed out.
// Tap the card to go on, swipe it (or use the arrows) to go back and forth. The words are in help-pages.js.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { h } from '/js/services/dom.js';
import { HELP, AROUND } from '/js/services/help-pages.js';
import { navMode } from '/js/services/nav-mode.js';

const CSS = '/css/pages/help.css';
const page = () => location.pathname.split('/').pop().replace(/\.html$/, '');

/** onClose runs once the dialog is gone (the round button uses it to settle back on the page you are on). */
export async function openHelp(onClose) {
    if (!document.querySelector(`link[href="${CSS}"]`)) document.head.append(h('link', { rel: 'stylesheet', href: CSS }));
    const entry = HELP[page()] || { title: 'Help', steps: [] };
    const steps = [...(typeof entry.steps === 'function' ? entry.steps() : entry.steps), ...(AROUND[navMode()] || AROUND['omni-wheel'])];
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Help', onClose, html: `<h3 class="glass-blur-dialog__title"></h3><div></div>` });
    panel.querySelector('.glass-blur-dialog__title').textContent = entry.title;
    const body = panel.lastElementChild;
    let at = 0, dir = '';
    const move = (d) => { const to = at + d; if (to < 0) return; if (to >= steps.length) return close(); at = to; dir = d > 0 ? 'next' : 'prev'; draw(); };
    const arrow = (d, label, glyph) => h('button', { class: 'hp__arrow', type: 'button', 'aria-label': label, disabled: d < 0 && at === 0, onclick: () => move(d), text: glyph });
    function draw() {
        const [head, text] = steps[at], last = at === steps.length - 1;
        const card = h('div', { class: 'hp__card', 'data-dir': dir, onclick: () => move(1) },   // the arrows are the keyboard way through
            h('strong', { text: head }), h('span', { text }), h('em', { class: 'hp__tap', text: last ? 'Tap to finish' : 'Tap to continue' }));
        let x0 = null;
        card.addEventListener('pointerdown', (e) => { x0 = e.clientX; });
        card.addEventListener('pointerup', (e) => { if (x0 === null) return; const dx = e.clientX - x0; x0 = null; if (Math.abs(dx) > 40) { card.dataset.swiped = '1'; move(dx < 0 ? 1 : -1); } });
        card.addEventListener('click', (e) => { if (card.dataset.swiped) e.stopImmediatePropagation(); }, true);   // a swipe is not also a tap
        body.replaceChildren(h('div', { class: 'hp' },
            h('div', { class: 'hp__dots', 'aria-hidden': 'true' }, ...steps.map((_, i) => h('i', { class: i === at ? 'is-on' : '' }))),
            h('div', { class: 'hp__row' }, arrow(-1, 'Back', '‹'), card, arrow(1, last ? 'Finish' : 'Next', last ? '✓' : '›'))));
    }
    panel.addEventListener('keydown', (e) => { if (e.key === 'ArrowRight') move(1); else if (e.key === 'ArrowLeft') move(-1); });
    draw();
}
