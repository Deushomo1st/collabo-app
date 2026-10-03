// Pull down at the top of the Gaze: the feed follows your finger and a ring draws itself, turning once round; letting go on a full circle refreshes.
import { h } from '/js/services/dom.js';
import { zoomOf } from '/js/services/ui-scale.js';

const MARK = 72, MAX = 130, HOLD = 56;   // px: where letting go refreshes, the furthest it stretches, where it rests while refreshing
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function pullToRefresh(refresh) {
    const ring = h('div', { class: 'ptr', 'aria-hidden': 'true' }, h('span', { class: 'ptr__ring' }));
    document.body.append(ring);
    const list = () => document.getElementById('list');
    let y0 = null, pull = 0, busy = false;

    const lay = (d, soft) => {   // the feed sits d px lower; the ring rides in the gap under the header
        const top = document.querySelector('.sp-top').getBoundingClientRect().bottom;
        const t = soft ? 'transform .3s cubic-bezier(.16, 1, .3, 1), opacity .3s' : 'none';
        list().style.transition = t; list().style.transform = d ? `translateY(${d}px)` : '';
        ring.style.transition = t; ring.style.top = `${top / zoomOf()}px`;
        ring.style.opacity = String(Math.min(1, d / (MARK * 0.8)));
        ring.style.transform = `translate(-50%, ${d / 2 - 18}px) scale(${0.6 + Math.min(1, d / MARK) * 0.4})`;
        const p = Math.min(1, d / MARK);   // the ring draws itself and turns once round by the time you reach the mark; a full circle means letting go loads
        if (!busy) { ring.firstChild.style.setProperty('--p', String(p)); ring.firstChild.style.transform = `rotate(${p * 360}deg)`; }
    };

    addEventListener('touchstart', (e) => { y0 = !busy && scrollY <= 0 && e.touches.length === 1 && !e.target.closest('.hold-fan-trigger, .hold-fan, .nav-selector-fluid-hold') ? e.touches[0].clientY : null; pull = 0; }, { passive: true });
    addEventListener('touchmove', (e) => {
        if (y0 === null) return;
        const dy = e.touches[0].clientY - y0;
        if (dy <= 0 || scrollY > 0) return void (pull && lay((pull = 0)));
        if (!pull && dy < 14) return;   // a tap wobbles a pixel or two; stopping that move would cancel the tap's click
        e.preventDefault();   // the page itself does not move, the feed does
        pull = Math.min(MAX, dy * 0.5);   // it gets heavier the further you pull
        lay(pull);
    }, { passive: false });
    const let_go = async () => {
        if (y0 === null) return;
        y0 = null;
        if (pull < MARK) return void lay((pull = 0), true);
        busy = true; ring.firstChild.style.setProperty('--p', '.75'); ring.firstChild.style.transform = ''; ring.classList.add('is-spinning'); lay(HOLD, true);
        try { await Promise.all([refresh(), sleep(1500)]); } catch { /* the feed says so itself */ }
        busy = false; ring.classList.remove('is-spinning'); lay((pull = 0), true);
    };
    addEventListener('touchend', let_go); addEventListener('touchcancel', let_go);
}
