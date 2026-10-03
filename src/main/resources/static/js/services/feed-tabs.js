// The Gaze-style switcher: plain text tabs in the header, the live one bold in the middle with the neighbours at the edges.
// A swipe sideways (on the tabs, or anywhere on the page) or a tap on an edge one brings that neighbour to the middle and the rest go round.
// labels: the choices in order; at(): index of the live one; go(i): the page switches to it; blocked(): true while swiping must be left alone.
import { h } from '/js/services/dom.js';

export function feedTabs(nav, { labels, at, go, blocked = () => false }) {
    const n = labels.length;
    const of = (d) => (at() + d + n) % n;
    let shown = -1;
    const draw = (dir = '') => {
        shown = at();
        nav.dataset.dir = dir;
        const around = n > 2 ? [-1, 0, 1] : n > 1 ? [0, 1] : [0];
        nav.replaceChildren(...around.map((d) => h('button', { class: `tt-tab${d < 0 ? ' is-prev' : d > 0 ? ' is-next' : ''}`, type: 'button', role: 'tab', 'aria-selected': String(d === 0), text: labels[of(d)], onclick: () => pick(d) })));
    };
    const pick = (d) => { if (!d) return; const i = of(d); go(i); draw(d > 0 ? 'next' : 'prev'); };
    let x0 = 0, swiped = false;
    nav.addEventListener('pointerdown', (e) => { x0 = e.clientX; swiped = false; });
    nav.addEventListener('pointerup', (e) => { if (Math.abs(e.clientX - x0) < 30) return; swiped = true; pick(e.clientX < x0 ? 1 : -1); });
    nav.addEventListener('click', (e) => { if (swiped) { e.stopPropagation(); swiped = false; } }, true);   // a swipe that ends on a tab is not also a tap on it
    // The same swipe anywhere on the page: a clear sideways stroke (not a scroll that drifted). Typing, the tab bar and dialogs keep their own gestures.
    let t0 = null;
    document.addEventListener('touchstart', (e) => { const t = e.touches[0]; t0 = e.touches.length === 1 && !e.target.closest('input, textarea, select, [role="dialog"]') && !nav.contains(e.target) ? { x: t.clientX, y: t.clientY } : null; }, { passive: true });
    document.addEventListener('touchend', (e) => {
        const t = e.changedTouches[0], s = t0; t0 = null;
        if (!s || blocked()) return;
        const dx = t.clientX - s.x, dy = t.clientY - s.y;
        if (Math.abs(dx) > 70 && Math.abs(dx) > 2 * Math.abs(dy)) pick(dx < 0 ? 1 : -1);
    }, { passive: true });
    draw();
    return { draw, sync: () => { if (shown !== at()) draw(); } };
}
