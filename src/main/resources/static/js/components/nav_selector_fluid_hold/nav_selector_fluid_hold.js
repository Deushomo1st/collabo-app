// Nav Selector component — loader and event logic.
// Renders the CSS-anchor "bubble" pill nav (Raul Dronke style).
// Usage: import { mountNavSelector } from '/js/components/nav_selector_fluid_hold/nav_selector_fluid_hold.js';
//        mountNavSelector('#nav_selector_fluid_hold');
//        mountNavSelector('#nav_selector_fluid_hold', { links: ['A', 'B'], activeIndex: 0, onChange: (label, href) => {} });

export async function mountNavSelector(targetSelector = '#nav_selector_fluid_hold', options = {}) {
    const target = document.querySelector(targetSelector);
    if (!target) return;

    loadStylesOnce('/js/components/nav_selector_fluid_hold/nav_selector_fluid_hold.css', 'nav_selector_fluid_hold');

    const res = await fetch('/js/components/nav_selector_fluid_hold/nav_selector_fluid_hold.html');
    const wrapper = document.createElement('div');
    wrapper.innerHTML = await res.text();
    const root = wrapper.firstElementChild;

    // Optional: swap the default links.
    if (Array.isArray(options.links) && options.links.length) {
        const nav = root.querySelector('.nav_selector_fluid_hold__nav');
        nav.textContent = '';
        const activeIndex = options.activeIndex ?? 0;
        options.links.forEach((label, i) => {
            const a = document.createElement('a');
            a.href = options.hrefs?.[i] || '#';
            a.className = 'nav_selector_fluid_hold__link' + (i === activeIndex ? ' is-active' : '');
            a.textContent = label;
            nav.appendChild(a);
        });
    }

    // Active-state toggle on click.
    const links = root.querySelectorAll('.nav_selector_fluid_hold__link');
    links.forEach((link) => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            if (link.classList.contains('is-active')) return;
            links.forEach((l) => l.classList.remove('is-active'));
            link.classList.add('is-active');
            if (typeof options.onChange === 'function') options.onChange(link.textContent, link.href);
        });
    });

    target.appendChild(root);
    void target.offsetHeight;
}

function loadStylesOnce(href, componentName) {
    if (document.querySelector(`link[data-component="${componentName}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.component = componentName;
    document.head.appendChild(link);
}
