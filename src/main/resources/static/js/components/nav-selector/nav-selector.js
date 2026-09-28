// Nav Selector component — loader and event logic.
// Renders the CSS-anchor "bubble" pill nav (Raul Dronke style).
// Usage: import { mountNavSelector } from '/js/components/nav-selector/nav-selector.js';
//        mountNavSelector('#nav-selector');
//        mountNavSelector('#nav-selector', { links: ['A', 'B'], activeIndex: 0, onChange: (label, href) => {} });

export async function mountNavSelector(targetSelector = '#nav-selector', options = {}) {
    const target = document.querySelector(targetSelector);
    if (!target) return;

    loadStylesOnce('/js/components/nav-selector/nav-selector.css', 'nav-selector');

    const res = await fetch('/js/components/nav-selector/nav-selector.html');
    const wrapper = document.createElement('div');
    wrapper.innerHTML = await res.text();
    const root = wrapper.firstElementChild;

    // Optional: swap the default links.
    if (Array.isArray(options.links) && options.links.length) {
        const nav = root.querySelector('.nav-selector__nav');
        nav.textContent = '';
        const activeIndex = options.activeIndex ?? 0;
        options.links.forEach((label, i) => {
            const a = document.createElement('a');
            a.href = options.hrefs?.[i] || '#';
            a.className = 'nav-selector__link' + (i === activeIndex ? ' is-active' : '');
            a.textContent = label;
            nav.appendChild(a);
        });
    }

    // Active-state toggle on click.
    const links = root.querySelectorAll('.nav-selector__link');
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
}

function loadStylesOnce(href, componentName) {
    if (document.querySelector(`link[data-component="${componentName}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.component = componentName;
    document.head.appendChild(link);
}
