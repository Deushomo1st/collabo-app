// hold-fan: press and hold a button and up to five round buttons fan out from behind it, all at once (stagger: ms between them, 0 by default).
// Keep the finger down and slide onto one, then let go: that is the pick. Let go anywhere else and the fan stays open to tap.
// A plain tap is untouched: the trigger's own click still fires. Built for the collapsed nav icon (nav-selector's `holdActions`
// option uses it), but it works on any button.
//
// Usage:
//   import { mountHoldFan } from '/js/components/hold-fan/hold-fan.js';
//   const fan = mountHoldFan(button, { actions: [
//       { label: 'Home', icon: '<svg>…</svg>', onSelect: () => (location.href = '/') },
//       { label: 'Report', icon: '<svg>…</svg>', onSelect: report },
//       { label: 'Back', icon: '<svg>…</svg>', onSelect: () => history.back() },
//   ] });
//   fan.destroy();   // when the trigger goes away

const CSS_HREF = '/js/components/hold-fan/hold-fan.css';
const SIZE = 46;                 // px, each round button
const RADIUS = 84;               // px, trigger centre to button centre
const ANGLES = { 1: [-90], 2: [-125, -55], 3: [-155, -90, -25], 4: [-160, -120, -60, -20], 5: [-160, -125, -90, -55, -20] };   // degrees from the x axis; negative = up. Left to right (5 has one dead centre).

// opts: { actions: [{ label, icon (svg markup, static), onSelect() }], holdMs = 350, stagger = 0, idleMs = 4000 }
// Returns { open(), close(), destroy() }.
export function mountHoldFan(trigger, opts = {}) {
    loadStylesOnce();
    const actions = (opts.actions || []).slice(0, 5);
    const holdMs = opts.holdMs ?? 350, stagger = opts.stagger ?? 0, idleMs = opts.idleMs ?? 4000;
    const radius = actions.length > 3 ? 90 : RADIUS;
    let timer = null, idle = null, layer = null, startX = 0, startY = 0, justHeld = false, hot = null;

    function close() {
        clearTimeout(idle);
        if (!layer) return;
        const old = layer; layer = null;
        old.classList.remove('is-open');
        old.querySelectorAll('.hold-fan__btn').forEach((b) => { b.tabIndex = -1; });
        setTimeout(() => old.remove(), stagger * actions.length + 300);   // let them tuck back in first
        document.removeEventListener('pointerdown', onOutside, true);
        document.removeEventListener('keydown', onKey, true);
        trigger.setAttribute('aria-expanded', 'false');
    }
    const onOutside = (e) => { if (layer && !layer.contains(e.target)) close(); };
    const onKey = (e) => { if (e.key === 'Escape') { close(); trigger.focus(); } };

    function open() {
        if (layer || !actions.length) return;
        const r = trigger.getBoundingClientRect();
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        layer = document.createElement('div');
        layer.className = 'hold-fan';
        layer.setAttribute('role', 'menu');
        layer.style.left = `${cx}px`; layer.style.top = `${cy}px`;
        actions.forEach((a, i) => {
            const rad = ANGLES[actions.length][i] * Math.PI / 180;
            // keep every button on screen when the trigger sits near an edge
            const x = Math.min(Math.max(Math.cos(rad) * radius, SIZE / 2 + 8 - cx), innerWidth - SIZE / 2 - 8 - cx);
            const b = document.createElement('button');
            b.type = 'button'; b.className = 'hold-fan__btn'; b.setAttribute('role', 'menuitem');
            b.setAttribute('aria-label', a.label); b.title = a.label;
            b.innerHTML = a.icon;   // static markup supplied by the caller, never user text
            b.style.setProperty('--x', `${x}px`);
            b.style.setProperty('--y', `${Math.sin(rad) * radius}px`);
            b.style.setProperty('--d', `${i * stagger}ms`);
            b.addEventListener('click', (e) => { e.stopPropagation(); close(); a.onSelect?.(); });
            layer.append(b);
        });
        document.body.append(layer);
        void layer.offsetWidth;                       // commit the hidden state, so the move is animated
        layer.classList.add('is-open');
        trigger.setAttribute('aria-expanded', 'true');
        document.addEventListener('pointerdown', onOutside, true);
        document.addEventListener('keydown', onKey, true);
        idle = setTimeout(close, idleMs);
        layer.addEventListener('pointerdown', () => clearTimeout(idle));
    }

    // Slide to pick: while the finger is still down after the hold opened the fan, the button under it lights up, and letting go on it picks it.
    const under = (e) => document.elementFromPoint(e.clientX, e.clientY)?.closest('.hold-fan__btn');
    const onSlide = (e) => {
        const b = under(e) || null;
        if (b === hot) return;
        hot?.classList.remove('is-hot'); hot = b; hot?.classList.add('is-hot');
    };
    function onRelease(e) {
        document.removeEventListener('pointermove', onSlide, true);
        document.removeEventListener('pointerup', onRelease, true);
        document.removeEventListener('pointercancel', onRelease, true);
        const pick = e.type === 'pointerup' ? under(e) : null;
        hot?.classList.remove('is-hot'); hot = null;
        setTimeout(() => { justHeld = false; }, 0);   // the click that follows this release has been swallowed by now
        if (pick && layer?.contains(pick)) pick.click();
    }
    function startSliding() {
        document.addEventListener('pointermove', onSlide, true);
        document.addEventListener('pointerup', onRelease, true);
        document.addEventListener('pointercancel', onRelease, true);
    }

    const cancel = () => clearTimeout(timer);
    const onDown = (e) => {
        if (e.button > 0) return;
        startX = e.clientX; startY = e.clientY; justHeld = false;
        cancel();
        timer = setTimeout(() => { justHeld = true; open(); if (layer) startSliding(); }, holdMs);
    };
    const onMove = (e) => { if (Math.hypot(e.clientX - startX, e.clientY - startY) > 10) cancel(); };   // a drag is not a hold
    // The release after a hold must not count as a tap (the nav would expand and cover the buttons).
    const onClick = (e) => { if (justHeld) { e.stopImmediatePropagation(); e.preventDefault(); justHeld = false; } };
    const noMenu = (e) => e.preventDefault();         // a long press opens a context menu on phones
    // Enter and Space are plain clicks, so the keyboard opens the fan with ArrowUp instead.
    const onKeyTrigger = (e) => { if (e.key === 'ArrowUp') { e.preventDefault(); open(); layer?.querySelector('button')?.focus(); } };

    trigger.classList.add('hold-fan-trigger');
    trigger.setAttribute('aria-haspopup', 'menu'); trigger.setAttribute('aria-expanded', 'false');
    trigger.addEventListener('pointerdown', onDown);
    trigger.addEventListener('pointermove', onMove);
    trigger.addEventListener('pointerup', cancel);
    trigger.addEventListener('pointercancel', cancel);
    trigger.addEventListener('pointerleave', cancel);
    trigger.addEventListener('click', onClick, true);   // capture: ahead of the trigger's own click handler
    trigger.addEventListener('contextmenu', noMenu);
    trigger.addEventListener('dragstart', noMenu);   // a picture inside (the profile face) must not be lifted off by the hold
    trigger.addEventListener('keydown', onKeyTrigger);

    return {
        open, close,
        destroy() {
            cancel(); close(); layer?.remove();
            document.removeEventListener('pointermove', onSlide, true);
            document.removeEventListener('pointerup', onRelease, true);
            document.removeEventListener('pointercancel', onRelease, true);
            trigger.removeEventListener('pointerdown', onDown);
            trigger.removeEventListener('pointermove', onMove);
            trigger.removeEventListener('pointerup', cancel);
            trigger.removeEventListener('pointercancel', cancel);
            trigger.removeEventListener('pointerleave', cancel);
            trigger.removeEventListener('click', onClick, true);
            trigger.removeEventListener('contextmenu', noMenu);
            trigger.removeEventListener('dragstart', noMenu);
            trigger.removeEventListener('keydown', onKeyTrigger);
        },
    };
}

function loadStylesOnce() {
    if (document.querySelector('link[data-component="hold-fan"]')) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet'; link.href = CSS_HREF; link.dataset.component = 'hold-fan';
    document.head.appendChild(link);
}
