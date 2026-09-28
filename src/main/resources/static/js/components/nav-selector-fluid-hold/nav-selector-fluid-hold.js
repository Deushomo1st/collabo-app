// Nav Selector component — "fluid hold" collapsible pill nav.
// Collapses to a centered icon on page scroll-down; expands on scroll-up,
// on icon click, and re-iconizes after 1.5s idle (configurable).
// Usage: import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
//        mountNavSelector('#nav-selector-fluid-hold');
//        mountNavSelector('#nav-selector-fluid-hold', { links: ['A','B'], hrefs: ['#a','#b'], activeIndex: 0, idleMs: 1500, onChange: (label, href) => {} });

const ICONS = [
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h0a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v0a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>',
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/></svg>'
];

export async function mountNavSelector(targetSelector = '#nav-selector-fluid-hold', options = {}) {
    const target = document.querySelector(targetSelector);
    if (!target) return;

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
            a.innerHTML = ICONS[i % ICONS.length] + '<span></span>';
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
    function syncIcon() {
        const active = root.querySelector('.nav-selector-fluid-hold__link.is-active');
        if (!active) return;
        const key = active.textContent.trim();
        if (navIcon.dataset.for !== key) {
            navIcon.textContent = '';
            const svg = active.querySelector('svg');
            if (svg) navIcon.appendChild(svg.cloneNode(true));
            navIcon.dataset.for = key;
        }
    }

    // ---------- iconize / expand state ----------
    let idleTimer = null;
    let lastY = window.scrollY;

    const isIconized = () => root.classList.contains('iconized');
    const iconize = () => { clearTimeout(idleTimer); syncIcon(); root.classList.add('iconized'); };
    const expand = () => root.classList.remove('iconized');

    function armIdle() {
        clearTimeout(idleTimer);
        idleTimer = setTimeout(() => {
            if (window.scrollY >= 40 && !root.matches(':hover')) iconize();
            else if (window.scrollY >= 40) armIdle();
        }, options.idleMs ?? 1500);
    }

    window.addEventListener('scroll', () => {
        const y = window.scrollY;
        const dy = y - lastY;
        lastY = y;
        if (y < 40) { clearTimeout(idleTimer); expand(); return; }
        if (dy > 2) iconize();
        else if (dy < -2) { expand(); armIdle(); }
    }, { passive: true });

    navIcon.addEventListener('click', () => { expand(); armIdle(); });
    root.addEventListener('mouseenter', () => clearTimeout(idleTimer));
    root.addEventListener('mouseleave', () => { if (window.scrollY >= 40 && !isIconized()) armIdle(); });

    // ---------- horizontal menu logic ----------
    function update() {
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
            link.scrollIntoView({ inline: 'nearest', behavior: 'smooth', block: 'nearest' });
            syncIcon();
            armIdle();
            setTimeout(update, 50);
            if (typeof options.onChange === 'function') options.onChange(link.textContent.trim(), link.href);
        });
    });

    arrowL.addEventListener('click', () => goTo(centerIndex() - 1));
    arrowR.addEventListener('click', () => goTo(centerIndex() + 1));
    view.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);

    // Mount inside a sticky wrapper so the pill stays reachable on long pages.
    const wrap = document.createElement('div');
    wrap.className = 'nav-selector-fluid-hold-wrap';
    wrap.appendChild(root);
    target.appendChild(wrap);
    void target.offsetHeight; // force reflow so anchor bubbles measure correctly

    syncIcon();
    update();
}

function loadStylesOnce(href, componentName) {
    if (document.querySelector(`link[data-component="${componentName}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.component = componentName;
    document.head.appendChild(link);
}