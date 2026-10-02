// Nav Selector component — "fluid hold" collapsible pill nav.
// Collapses to a centered icon on scroll-down; expands only when you tap the icon
// (or hold it, with holdActions), and re-iconizes after 1.5s idle (configurable; idleMs: 0 turns the idle collapse off).
// Usage: import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
//        const nav = await mountNavSelector('#nav-selector-fluid-hold');
//        mountNavSelector('#nav-selector-fluid-hold', { links: ['A','B'], hrefs: ['#a','#b'], activeIndex: 0, idleMs: 1500, onChange: (label, href) => {} });
//        mountNavSelector(el, { scrollRoot: document.querySelector('main') });   // content scrolls inside <main>
//        mountNavSelector(el, { placement: 'bottom', align: 'start' });           // docked bottom-left, grows rightwards
//        mountNavSelector(el, { collapseWhenIdle: true, idleMs: 3000 });          // collapse after 3s idle, even at the top; it also starts collapsed (startIconized: false = start open)
//        mountNavSelector(el, { collapsedLabel: 'number' });                      // collapsed pill shows "3" instead of the icon
//        mountNavSelector(el, { holdActions: [{ label, icon, onSelect }] });       // hold the collapsed icon: up to 5 buttons fan out (slide onto one and let go to pick it) (hold-fan)
//        nav.setActive(2);                                                         // select a link from code (no onChange)
//        nav.destroy();                                                            // remove listeners + DOM
// Scrolling is watched on `scrollRoot` (default: the window). If nothing there scrolls, it never iconizes.

import { mountHoldFan } from '/js/components/hold-fan/hold-fan.js';

const ICONS = [
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h0a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v0a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/></svg>'
];

export async function mountNavSelector(targetSelector = '#nav-selector-fluid-hold', options = {}) {
    const target = typeof targetSelector === 'string' ? document.querySelector(targetSelector) : targetSelector;
    if (!target) return;

    // Where scrolling happens: the window (default) or a scrollable element (or its selector).
    const scrollRoot = typeof options.scrollRoot === 'string'
        ? document.querySelector(options.scrollRoot)
        : (options.scrollRoot || window);
    const getY = () => (scrollRoot === window ? window.scrollY : scrollRoot.scrollTop);
    const threshold = options.threshold ?? 40;   // px from the top where it always stays expanded

    loadStylesOnce('/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.css', 'nav-selector-fluid-hold');

    const res = await fetch('/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.html');
    const wrapper = document.createElement('div');
    wrapper.innerHTML = await res.text();
    const root = wrapper.firstElementChild;

    // Optional: swap the default links (icons cycle from ICONS).
    if (Array.isArray(options.links) && options.links.length) {
        const nav = root.querySelector('.nav-selector-fluid-hold__nav');
        nav.textContent = '';
        const activeIndex = options.activeIndex ?? 0;
        options.links.forEach((label, i) => {
            const a = document.createElement('a');
            a.href = options.hrefs?.[i] || '#';
            a.className = 'nav-selector-fluid-hold__link' + (i === activeIndex ? ' is-active' : '');
            a.innerHTML = (options.icons?.[i] ?? ICONS[i % ICONS.length]) + '<span></span>';
            a.querySelector('span').textContent = label;
            nav.appendChild(a);
        });
    }

    const clip = root.querySelector('.nav-selector-fluid-hold__clip');
    const view = root.querySelector('.nav-selector-fluid-hold__viewport');
    const arrowL = root.querySelector('.nav-selector-fluid-hold__arrow--left');
    const arrowR = root.querySelector('.nav-selector-fluid-hold__arrow--right');
    const navIcon = root.querySelector('.nav-selector-fluid-hold__icon');
    const getLinks = () => [...root.querySelectorAll('.nav-selector-fluid-hold__link')];

    // ---------- icon sync ----------
    // options.restIcon (an svg string): the page's own icon, shown on the collapsed button until a link is picked.
    let picked = false;
    function syncIcon() {
        const active = root.querySelector('.nav-selector-fluid-hold__link.is-active');
        if (options.restIcon && !picked) {
            if (navIcon.dataset.for !== '(rest)') {
                if (typeof options.restIcon === 'function') navIcon.replaceChildren(options.restIcon());   // e.g. a profile picture
                else navIcon.innerHTML = options.restIcon;
                navIcon.dataset.for = '(rest)';
                navIcon.setAttribute('aria-label', 'Show navigation');
            }
            return;
        }
        if (!active) return;
        const key = active.textContent.trim();
        if (navIcon.dataset.for !== key) {
            navIcon.textContent = '';
            if (options.collapsedLabel === 'number') {
                const num = document.createElement('span');
                num.className = 'nav-selector-fluid-hold__number';
                num.textContent = String(getLinks().indexOf(active) + 1);
                navIcon.appendChild(num);
            } else {
                const svg = active.querySelector('svg, i');   // <i> = a picture icon (e.g. a profile face)
                if (svg) navIcon.appendChild(svg.cloneNode(true));
            }
            navIcon.dataset.for = key;
            navIcon.setAttribute('aria-label', `Show navigation (current: ${key})`);
        }
    }

    // ---------- iconize / expand state ----------
    let idleTimer = null;
    let lastY = getY();

    const isIconized = () => root.classList.contains('iconized');
    const iconize = () => {
        clearTimeout(idleTimer); syncIcon(); root.classList.add('iconized');
        // scrolled away with the arrows and picked nothing: reopen on the lineup that holds the black focus
        setTimeout(() => { if (isIconized() && windowed() && !drag && !settling) { start = homeStart(); showWindow(); } }, 250);
    };
    const expand = () => root.classList.remove('iconized');

    // collapseWhenIdle: collapse after idleMs anywhere on the page, not only below `threshold`.
    const idleAnywhere = options.collapseWhenIdle === true;
    const canIdleCollapse = () => idleAnywhere || getY() >= threshold;
    // Busy = pointer over it, or keyboard focus inside it (a mouse click's leftover focus doesn't count).
    const isBusy = () => root.matches(':hover') || !!root.querySelector(':focus-visible');

    function armIdle() {
        clearTimeout(idleTimer);
        if (options.idleMs === 0) return;   // 0 = never collapse on idle: only a scroll down or a tap outside tucks it away
        idleTimer = setTimeout(() => {
            if (!canIdleCollapse() || isIconized()) return;
            if (isBusy()) armIdle();           // still in use: check again later
            else iconize();
        }, options.idleMs ?? 1500);
    }

    // Scrolling only tucks the nav away. It never opens it: that is a tap on the icon, or a hold (holdActions).
    function onScroll() {
        const y = getY();
        const dy = y - lastY;
        lastY = y;
        if (y >= threshold && dy > 2) iconize();
    }
    scrollRoot.addEventListener('scroll', onScroll, { passive: true });

    // Pressing anywhere outside the nav (another surface) tucks it away at once.
    const onOutside = (e) => { if (!root.contains(e.target) && !arrowL.contains(e.target) && !arrowR.contains(e.target) && !isIconized()) iconize(); };
    document.addEventListener('pointerdown', onOutside, true);

    navIcon.addEventListener('click', () => { expand(); armIdle(); });
    const fan = options.holdActions?.length ? mountHoldFan(navIcon, { actions: options.holdActions }) : null;
    root.addEventListener('mouseenter', () => clearTimeout(idleTimer));
    root.addEventListener('mouseleave', () => { if (canIdleCollapse() && !isIconized()) armIdle(); });
    // Keyboard: leaving the nav with Tab restarts the countdown.
    root.addEventListener('focusout', (e) => {
        if (!root.contains(e.relatedTarget) && canIdleCollapse() && !isIconized()) armIdle();
    });

    // ---------- horizontal menu logic ----------
    // The "hybrid" (default; options.visible = the most links shown, 3 unless set; visible: 0 = the old scrolling pill):
    // only V links show at once and the pill hugs them. V shrinks to 2 or 1 when the screen is too narrow.
    // The arrows (outside the pill) slide the window one link at a time; a drag locks onto the nearest window.
    const hybrid = options.visible !== 0;
    let V = hybrid ? Math.min(options.visible || 3, getLinks().length) : 0;
    let start = 0;
    const windowed = () => hybrid;
    const movable = () => hybrid && getLinks().length > V;
    function showWindow() {
        const links = getLinks();
        start = Math.max(0, Math.min(links.length - V, start));
        links.forEach((l, i) => { l.hidden = i < start || i >= start + V; });
        root.classList.toggle('active-hidden', !!root.querySelector('.nav-selector-fluid-hold__link.is-active[hidden]'));
        arrowL.classList.toggle('on', start > 0);   // 'on' = there is more that way; the arrow itself always shows
        arrowR.classList.toggle('on', start < links.length - V);
        root.classList.toggle('has-more', links.length > V);   // no arrows when everything already fits
    }
    const homeStart = () => Math.max(0, getLinks().findIndex((l) => l.classList.contains('is-active')) - Math.floor((V - 1) / 2));

    // How many links fit: the pill's room is the narrower of the screen rule and the container, less the two arrows and the pill's rim.
    function fitV() {
        if (!hybrid || drag || settling) return;
        const links = getLinks(), top = Math.min(options.visible || 3, links.length);
        const nv = getComputedStyle(wrap).getPropertyValue('--nav-width').trim();
        const screen = innerWidth <= 600 ? (nv.endsWith('px') ? parseFloat(nv) : (parseFloat(nv) || 94) * innerWidth / 100) : Math.min(.92 * innerWidth, 560);
        const room = Math.min(screen, target.clientWidth || screen) - 68 - 12;
        const hid = links.map((l) => l.hidden); links.forEach((l) => { l.hidden = false; });
        const fits = (k) => links.every((_, i) => i + k > links.length || links[i + k - 1].offsetLeft + links[i + k - 1].offsetWidth - links[i].offsetLeft + 2 * pad() <= room);
        let k = top; while (k > 1 && !fits(k)) k--;
        links.forEach((l, i) => { l.hidden = hid[i]; });
        if (k !== V) {
            V = k;
            const act = links.findIndex((l) => l.classList.contains('is-active'));
            if (act >= 0 && (act < start || act >= start + V)) start = act < start ? act : act - V + 1;
        }
        showWindow();
    }
    const reveal = (i) => { if (i < start) start = i; else if (i >= start + V) start = i - V + 1; showWindow(); };

    function update() {
        if (windowed()) return showWindow();
        const canL = view.scrollLeft > 2;
        const canR = view.scrollLeft < view.scrollWidth - view.clientWidth - 2;
        arrowL.classList.toggle('on', canL);
        arrowR.classList.toggle('on', canR);
        clip.classList.toggle('fade-l', canL);
        clip.classList.toggle('fade-r', canR);
    }

    function centerIndex() {
        const links = getLinks();
        const vc = view.getBoundingClientRect().left + view.clientWidth / 2;
        let best = 0, bd = Infinity;
        links.forEach((l, i) => {
            const r = l.getBoundingClientRect();
            const d = Math.abs((r.left + r.right) / 2 - vc);
            if (d < bd) { bd = d; best = i; }
        });
        return best;
    }

    // Select a link from code: same visual result as a click, but without onChange.
    function setActive(i) {
        const links = getLinks();
        const link = links[i];
        if (!link) return false;
        links.forEach(l => l.classList.remove('is-active'));
        link.classList.add('is-active');
        if (windowed()) reveal(i); else link.scrollIntoView({ inline: 'nearest', behavior: 'smooth', block: 'nearest' });
        picked = false;
        syncIcon();
        setTimeout(update, 50);
        return true;
    }

    function goTo(i) {
        const links = getLinks();
        i = Math.max(0, Math.min(links.length - 1, i));
        const l = links[i];
        view.scrollTo({ left: l.offsetLeft + l.offsetWidth / 2 - view.clientWidth / 2, behavior: 'smooth' });
    }

    getLinks().forEach(link => {
        link.addEventListener('click', e => {
            e.preventDefault();
            getLinks().forEach(l => l.classList.remove('is-active'));
            link.classList.add('is-active');
            if (!windowed()) link.scrollIntoView({ inline: 'nearest', behavior: 'smooth', block: 'nearest' });
            picked = link.getAttribute('href') !== '#';   // an in-page action (profile Settings) keeps the page's own resting icon
            syncIcon();
            armIdle();
            setTimeout(update, 50);
            if (typeof options.onChange === 'function') options.onChange(link.textContent.trim(), link.href);
        });
    });

    // swipe / sideways scroll: the links follow the finger (fluid), then lock onto the next or previous V and the pill morphs to fit them
    const navEl = root.querySelector('.nav-selector-fluid-hold__nav');
    const pad = () => parseFloat(getComputedStyle(navEl).paddingLeft) || 0;   // the nav's side padding (read late: the is-windowed class sets it)
    const EASE = '.3s cubic-bezier(.2,.8,.2,1)';
    const offsetOf = (i) => getLinks()[i].offsetLeft - pad();
    const spanOf = (i) => { const a = getLinks()[i], b = getLinks()[i + V - 1]; return b.offsetLeft + b.offsetWidth - a.offsetLeft + 2 * pad(); };
    let swipeX = null, swiped = false, drag = null, settling = false;
    function dragMove(dx) {
        if (settling) return;
        if (!drag) {
            drag = { w: view.offsetWidth, base: 0 };
            root.classList.add('is-dragging');
            getLinks().forEach((l) => { l.hidden = false; });
            view.scrollLeft = 0;
            drag.base = offsetOf(start);
            view.style.width = drag.w + 'px'; navEl.style.transition = 'none';
        }
        swiped = true;
        navEl.style.transform = `translateX(${-Math.max(0, Math.min(offsetOf(getLinks().length - V), drag.base - dx))}px)`;
        drag.dx = dx;
    }
    function dragEnd() {
        if (!drag) return;
        const dx = drag.dx || 0, base = drag.base; drag = null; settling = true;
        // lock onto the window whose first link is nearest to where the drag landed; a flick that would round back moves one link
        const last = getLinks().length - V, pos = Math.max(0, Math.min(offsetOf(last), base - dx));
        let to = 0;
        for (let i = 1; i <= last; i++) if (Math.abs(offsetOf(i) - pos) < Math.abs(offsetOf(to) - pos)) to = i;
        if (to === start && Math.abs(dx) > 25) to = Math.max(0, Math.min(last, start + (dx < 0 ? 1 : -1)));
        view.style.transition = 'width ' + EASE; navEl.style.transition = 'transform ' + EASE;
        view.style.width = spanOf(to) + 'px'; navEl.style.transform = `translateX(${-offsetOf(to)}px)`;
        setTimeout(() => {
            start = to; view.removeAttribute('style'); navEl.removeAttribute('style'); showWindow(); view.scrollLeft = 0;
            root.classList.remove('is-dragging'); settling = false; armIdle();
        }, 320);
    }
    root.addEventListener('pointerdown', (e) => { swipeX = movable() && !settling ? e.clientX : null; swiped = false; });
    const onSwipeMove = (e) => {
        if (e.pointerType === 'mouse' && e.isTrusted && !e.buttons) return onSwipeEnd();   // the release was missed (off the window): let go
        if (swipeX !== null && (drag || Math.abs(e.clientX - swipeX) > 6)) dragMove(e.clientX - swipeX); };
    const onSwipeEnd = () => { swipeX = null; dragEnd(); };   // on the document: a drag that ends off the pill still counts
    document.addEventListener('pointermove', onSwipeMove);
    document.addEventListener('pointerup', onSwipeEnd);
    document.addEventListener('pointercancel', onSwipeEnd);
    let wheelDx = 0, wheelT = 0;
    root.addEventListener('wheel', (e) => {   // trackpad or shift-wheel sideways
        if (!movable() || Math.abs(e.deltaX) <= Math.abs(e.deltaY)) return;
        e.preventDefault(); wheelDx -= e.deltaX; dragMove(wheelDx);
        clearTimeout(wheelT); wheelT = setTimeout(() => { wheelDx = 0; dragEnd(); }, 140);
    }, { passive: false });
    root.addEventListener('click', (e) => { if (swiped) { e.preventDefault(); e.stopImmediatePropagation(); swiped = false; } }, true);
    const step = (d) => { if (windowed()) { start += d; showWindow(); armIdle(); } else goTo(centerIndex() + d); };
    arrowL.addEventListener('click', () => step(-1));
    arrowR.addEventListener('click', () => step(1));
    view.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    const refit = () => fitV();
    window.addEventListener('resize', refit);
    const ro = typeof ResizeObserver === 'function' ? new ResizeObserver(refit) : null;

    // Mount inside a sticky wrapper so the pill stays reachable on long pages.
    const wrap = document.createElement('div');
    wrap.className = 'nav-selector-fluid-hold-wrap'
        + (options.placement === 'bottom' ? ' nav-selector-fluid-hold-wrap--bottom' : '')
        + (options.align === 'start' ? ' nav-selector-fluid-hold-wrap--start' : '');
    if (hybrid) {   // the arrows sit either side of the pill, not inside it
        wrap.classList.add('nav-selector-fluid-hold-wrap--arrows');
        root.classList.add('is-windowed');
        wrap.append(arrowL, root, arrowR);
        start = homeStart();
    } else wrap.appendChild(root);
    target.appendChild(wrap);
    void target.offsetHeight; // force reflow so anchor bubbles measure correctly

    syncIcon();
    update();
    if (hybrid) { fitV(); ro?.observe(target); }
    if (idleAnywhere && options.startIconized !== false) {   // a page that just opened shows the icon, not the open pill (startIconized: false to opt out)
        root.style.transition = 'none'; root.classList.add('iconized'); void root.offsetWidth; root.style.transition = '';   // no shrink animation on load
    } else if (idleAnywhere) armIdle();        // start the countdown straight away

    return {
        element: root,
        setActive,
        destroy() {
            clearTimeout(idleTimer);
            fan?.destroy();
            scrollRoot.removeEventListener('scroll', onScroll);
            document.removeEventListener('pointerdown', onOutside, true);
            document.removeEventListener('pointerup', onSwipeEnd);
            document.removeEventListener('pointercancel', onSwipeEnd);
            document.removeEventListener('pointermove', onSwipeMove);
            window.removeEventListener('resize', update);
            window.removeEventListener('resize', refit);
            ro?.disconnect();
            wrap.remove();
        },
    };
}

function loadStylesOnce(href, componentName) {
    if (document.querySelector(`link[data-component="${componentName}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.component = componentName;
    document.head.appendChild(link);
}