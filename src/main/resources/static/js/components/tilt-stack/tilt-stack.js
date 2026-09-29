// tilt-stack: side-scrolling row of overlapping 3D-tilted 4:3 cards.
// Ported from SchoolHub app/tiltstack (renderTiltStack / highlightTiltCard).
//
// Usage:
//   import { renderTiltStack, preloadTiltStack } from '/js/components/tilt-stack/tilt-stack.js';
//   preloadTiltStack();                              // optional: load the CSS early (render is synchronous)
//   const stack = renderTiltStack(document.querySelector('#people'), [
//       { id: 1, name: 'Ada Obi', subtitle: 'Teacher', avatar: '/img/ada.jpg', status: 'active' },
//   ], { onClick: (item) => {}, showStatus: true });
//   stack.highlight(1);                              // scroll to a card and raise it
//
// Re-render by calling renderTiltStack again on the same container (e.g. after filtering).

const CSS_HREF = '/js/components/tilt-stack/tilt-stack.css';

export function preloadTiltStack() {
    return loadStylesOnce(CSS_HREF, 'tilt-stack');
}

// items: [{ id, name, subtitle?, avatar?, status? }]
// opts:  { onClick(item), showStatus, statusTone(status) -> 'ok'|'warn'|'danger'|'', emptyText, highlightId, label }
// Returns { element, highlight(id), destroy() }.
export function renderTiltStack(container, items, opts = {}) {
    preloadTiltStack().catch(() => {});

    const strip = document.createElement('div');
    strip.className = 'tilt-stack';
    if (opts.label) strip.setAttribute('aria-label', opts.label);
    container.replaceChildren(strip);

    const handle = {
        element: strip,
        highlight: (id) => highlightTiltCard(container, id),
        destroy: () => container.replaceChildren(),
    };

    if (!items || !items.length) {
        strip.classList.add('tilt-stack--empty');
        const p = document.createElement('p');
        p.className = 'tilt-stack__empty';
        p.textContent = opts.emptyText || 'Nothing to show yet.';
        strip.appendChild(p);
        return handle;
    }

    const clickable = typeof opts.onClick === 'function';
    items.forEach((it) => strip.appendChild(makeCard(it, opts, clickable)));
    if (opts.highlightId != null) highlightTiltCard(container, opts.highlightId, { scroll: false });

    // ---- Drag to scroll (mouse only: touch already scrolls natively) ----
    let drag = null;
    let suppressClick = false;
    strip.addEventListener('pointerdown', (ev) => {
        if (ev.pointerType !== 'mouse' || ev.button !== 0) return;
        drag = { x: ev.clientX, left: strip.scrollLeft, moved: false };
    });
    strip.addEventListener('pointermove', (ev) => {
        if (!drag) return;
        const dx = ev.clientX - drag.x;
        if (!drag.moved && Math.abs(dx) > 3) {
            drag.moved = true;
            strip.classList.add('tilt-stack--dragging');
        }
        if (drag.moved) strip.scrollLeft = drag.left - dx;
    });
    function endDrag() {
        if (drag && drag.moved) suppressClick = true;   // swallow the click that ends a drag
        drag = null;
        strip.classList.remove('tilt-stack--dragging');
    }
    strip.addEventListener('pointerup', endDrag);
    strip.addEventListener('pointerleave', endDrag);
    strip.addEventListener('pointercancel', endDrag);

    // ---- Vertical wheel scrolls sideways, but hands back to the page at either end ----
    strip.addEventListener('wheel', (ev) => {
        if (Math.abs(ev.deltaY) <= Math.abs(ev.deltaX)) return;          // trackpad sideways: native
        const max = strip.scrollWidth - strip.clientWidth;
        const atStart = strip.scrollLeft <= 0 && ev.deltaY < 0;
        const atEnd = strip.scrollLeft >= max - 1 && ev.deltaY > 0;
        if (max <= 0 || atStart || atEnd) return;                        // let the page scroll
        strip.scrollLeft += ev.deltaY;
        ev.preventDefault();
    }, { passive: false });

    if (clickable) {
        strip.addEventListener('click', (ev) => {
            const card = ev.target.closest('.tilt-stack__card');
            if (!card) return;
            if (suppressClick) { suppressClick = false; return; }
            fire(card);
        });
        strip.addEventListener('keydown', (ev) => {
            const card = ev.target.closest('.tilt-stack__card');
            if (!card || (ev.key !== 'Enter' && ev.key !== ' ')) return;
            ev.preventDefault();
            fire(card);
        });
    }
    function fire(card) {
        const it = items.find((x) => String(x.id) === card.dataset.id);
        if (it) opts.onClick(it);
    }

    return handle;
}

// Scroll a card to the centre and keep it raised (name shown), as if hovered.
export function highlightTiltCard(container, id, { scroll = true } = {}) {
    const card = container.querySelector(`.tilt-stack__card[data-id="${CSS.escape(String(id))}"]`);
    if (!card) return false;
    container.querySelectorAll('.tilt-stack__card--spotlight')
        .forEach((c) => c.classList.remove('tilt-stack__card--spotlight'));
    card.classList.add('tilt-stack__card--spotlight');
    if (scroll) card.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
    return true;
}

function makeCard(it, opts, clickable) {
    const card = document.createElement('div');
    card.className = 'tilt-stack__card' + (clickable ? ' tilt-stack__card--clickable' : '');
    card.dataset.id = String(it.id);
    card.tabIndex = 0;
    if (clickable) card.setAttribute('role', 'button');
    card.setAttribute('aria-label', it.subtitle ? `${it.name}, ${it.subtitle}` : String(it.name || ''));

    const name = document.createElement('div');
    name.className = 'tilt-stack__name';
    name.setAttribute('aria-hidden', 'true');
    name.textContent = it.name || '';
    if (it.subtitle) {
        const sub = document.createElement('span');
        sub.className = 'tilt-stack__sub';
        sub.textContent = it.subtitle;
        name.appendChild(sub);
    }

    const face = document.createElement('div');
    face.className = 'tilt-stack__face';
    const fallback = () => {
        const f = document.createElement('span');
        f.className = 'tilt-stack__fallback';
        f.textContent = initials(it.name);
        return f;
    };
    if (it.avatar) {
        const img = document.createElement('img');
        img.className = 'tilt-stack__img';
        img.src = it.avatar;
        img.alt = '';
        img.loading = 'lazy';
        img.draggable = false;                                   // native image drag would break drag-to-scroll
        img.addEventListener('error', () => img.replaceWith(fallback()), { once: true });
        face.appendChild(img);
    } else {
        face.appendChild(fallback());
    }

    if (opts.showStatus && it.status) {
        const badge = document.createElement('span');
        const tone = typeof opts.statusTone === 'function' ? opts.statusTone(it.status) : '';
        badge.className = 'tilt-stack__status' + (tone ? ` tilt-stack__status--${tone}` : '');
        badge.textContent = it.status;
        face.appendChild(badge);
    }

    card.append(name, face);
    return card;
}

function initials(name) {
    const p = String(name || '').trim().split(/\s+/);
    return (((p[0] || '')[0] || '') + ((p[1] || '')[0] || '')).toUpperCase() || '?';
}

function loadStylesOnce(href, componentName) {
    const existing = document.querySelector(`link[data-component="${componentName}"]`);
    if (existing) return existing.sheet ? Promise.resolve() : new Promise((r) => existing.addEventListener('load', r, { once: true }));
    return new Promise((resolve, reject) => {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = href;
        link.dataset.component = componentName;
        link.onload = resolve;
        link.onerror = () => reject(new Error(`${componentName}: stylesheet failed to load`));
        document.head.appendChild(link);
    });
}
