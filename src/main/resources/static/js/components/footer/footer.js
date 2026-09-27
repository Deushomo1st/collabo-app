// Footer component — loader and event logic.
// Usage: import { mountFooter } from '/js/components/footer/footer.js';
//        mountFooter('#footer');

export async function mountFooter(targetSelector = '#footer') {
    const target = document.querySelector(targetSelector);
    if (!target) return;

    loadStylesOnce('/js/components/footer/footer.css', 'footer');

    const res = await fetch('/js/components/footer/footer.html');
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
