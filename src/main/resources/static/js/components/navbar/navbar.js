// Navbar component — loader and event logic.
// Fetches its own markup and scoped styles, then mounts into the target.
// Usage: import { mountNavbar } from '/js/components/navbar/navbar.js';
//        mountNavbar('#navbar');

export async function mountNavbar(targetSelector = '#navbar') {
    const target = document.querySelector(targetSelector);
    if (!target) return;

    loadStylesOnce('/js/components/navbar/navbar.css', 'navbar');

    const res = await fetch('/js/components/navbar/navbar.html');
    target.innerHTML = await res.text();
}

function loadStylesOnce(href, componentName) {
    if (document.querySelector(`link[data-component="${componentName}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.component = componentName;
    document.head.appendChild(link);
}
