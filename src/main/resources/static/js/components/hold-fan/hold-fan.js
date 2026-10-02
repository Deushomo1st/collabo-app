// hold-fan: press and hold a button and up to five round buttons fan out from behind it, all at once (stagger: ms between them, 0 by default).
// Keep the finger down and push toward one (the pointer hides and snaps to the buttons' angles), then let go: that is the pick, and the
// fan closes on release, picked or not. Opened from the keyboard (ArrowUp) it stays open until you pick one, press Escape or tap elsewhere.
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

// opts: { actions: [{ label, icon (svg markup, static), onSelect() }], holdMs = 350, stagger = 0 }. A hold opens it and letting go closes it (picking the button you aimed at); from the keyboard it stays open until you pick one, Escape or tap elsewhere. No idle timer.
// Returns { open(), close(), destroy() }.
export function mountHoldFan(trigger, opts = {}) {
    loadStylesOnce();
    const actions = (opts.actions || []).slice(0, 5);
    const holdMs = opts.holdMs ?? 350, stagger = opts.stagger ?? 0;
    const radius = actions.length > 3 ? 90 : RADIUS;
    let timer = null, layer = null, startX = 0, startY = 0, justHeld = false, eatTap = false, hot = null;

    function close() {
        if (!layer) return;
        const old = layer; layer = null;
        old.classList.remove('is-open');
        old.querySelectorAll('.hold-fan__btn').forEach((b) => { b.tabIndex = -1; });
        setTimeout(() => old.remove(), stagger * actions.length + 300);   // let them tuck back in first
        document.removeEventListener('pointerdown', onOutside, true);
        document.removeEventListener('keydown', onKey, true);
        trigger.setAttribute('aria-expanded', 'false');
    }
    const onOutside = (e) => {
        if (!layer || layer.contains(e.target)) return;
        if (trigger.contains(e.target)) { eatTap = true; setTimeout(() => { eatTap = false; }, 400); }   // a tap on the trigger only closes the fan; its click must not also open the nav
        close();
    };
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
            b._angle = Math.atan2(Math.sin(rad) * radius, x);   // where it really sits (an edge can push it sideways)
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
    }

    // Slide to pick: while the hold is still down the pointer is hidden and locked to the buttons' angles: whichever button lies closest to the
    // direction you push in lights up and grows (no need to reach it), and letting go picks it and closes the fan. Letting go within DEAD px of
    // the start picks nothing. A mouse is Pointer Locked, so it can't wander off the window and reappears on the trigger when the lock ends;
    // a finger (or a refused lock) uses plain coordinates. Losing the window, a cancel or Escape ends the gesture with no pick.
    const DEAD = 24, REACH = 120;     // px: below DEAD nothing is aimed at; the locked pointer's travel is capped at REACH so the way back is short
    let locked = false, vx = 0, vy = 0, sliding = false, kind = 'mouse';
    const aim = (e) => {
        let dx = vx, dy = vy;
        if (!locked) { const r = trigger.getBoundingClientRect(); dx = e.clientX - (r.left + r.width / 2); dy = e.clientY - (r.top + r.height / 2); }
        if (!layer || Math.hypot(dx, dy) < DEAD) return null;
        const a = Math.atan2(dy, dx);
        let best = null, gap = Infinity;
        layer.querySelectorAll('.hold-fan__btn').forEach((b) => {
            const d = Math.abs(Math.atan2(Math.sin(a - b._angle), Math.cos(a - b._angle)));   // shortest way round
            if (d < gap) { gap = d; best = b; }
        });
        return best;
    };
    const onSlide = (e) => {
        if (locked) {
            vx += e.movementX; vy += e.movementY;
            const m = Math.hypot(vx, vy); if (m > REACH) { vx *= REACH / m; vy *= REACH / m; }
        }
        const b = aim(e);
        if (b === hot) return;
        hot?.classList.remove('is-hot'); hot = b; hot?.classList.add('is-hot');
    };
    function finish(pick) {
        if (!sliding) return;
        sliding = false;
        document.removeEventListener('pointermove', onSlide, true);
        document.removeEventListener('pointerup', onRelease, true);
        document.removeEventListener('pointercancel', onAbort, true);
        document.removeEventListener('pointerlockchange', onLock);
        window.removeEventListener('blur', onAbort);
        document.removeEventListener('visibilitychange', onAbort);
        document.documentElement.classList.remove('hold-fan-grab');
        locked = false;
        if (document.pointerLockElement) document.exitPointerLock();   // the pointer comes back where the hold began
        hot?.classList.remove('is-hot'); hot = null;
        setTimeout(() => { justHeld = false; }, 0);   // the click that follows this release has been swallowed by now
        if (pick) pick.click(); else close();          // a pick closes the fan itself; letting go on nothing closes it too
    }
    const onRelease = (e) => finish(aim(e));
    const onAbort = () => finish(null);
    const onLock = () => {
        if (document.pointerLockElement === document.documentElement) { locked = true; vx = vy = 0; }
        else if (locked) onAbort();                    // Escape (or the browser) took the lock away
    };
    function startSliding() {
        sliding = true; vx = vy = 0;
        document.documentElement.classList.add('hold-fan-grab');
        document.addEventListener('pointermove', onSlide, true);
        document.addEventListener('pointerup', onRelease, true);
        document.addEventListener('pointercancel', onAbort, true);
        document.addEventListener('pointerlockchange', onLock);
        window.addEventListener('blur', onAbort);
        document.addEventListener('visibilitychange', onAbort);
        // ponytail: needs the browser's recent-click allowance; if it refuses (or on touch) we just keep using coordinates
        if (kind === 'mouse') try { document.documentElement.requestPointerLock()?.catch?.(() => {}); } catch { /* coordinates it is */ }
    }

    const cancel = () => clearTimeout(timer);
    const onDown = (e) => {
        if (e.button > 0) return;
        startX = e.clientX; startY = e.clientY; justHeld = false; kind = e.pointerType;
        cancel();
        timer = setTimeout(() => { justHeld = true; open(); if (layer) startSliding(); }, holdMs);
    };
    const onMove = (e) => { if (Math.hypot(e.clientX - startX, e.clientY - startY) > 10) cancel(); };   // a drag is not a hold
    // The release after a hold, and a tap on the trigger that closes an open fan, must not count as a tap (the nav would expand).
    const onClick = (e) => { if (justHeld || eatTap) { e.stopImmediatePropagation(); e.preventDefault(); justHeld = eatTap = false; } };
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
            finish(null);
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
