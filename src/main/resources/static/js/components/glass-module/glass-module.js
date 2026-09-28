// glass-module: CSS-only frosted surface. This loader just injects the stylesheet once.
//
// Usage (either works):
//   1. <link rel="stylesheet" href="/js/components/glass-module/glass-module.css">
//   2. import { loadGlassModule } from '/js/components/glass-module/glass-module.js';
//      await loadGlassModule();
// Then: <section class="glass-module"> … </section>

const HREF = '/js/components/glass-module/glass-module.css';

export function loadGlassModule() {
    const existing = document.querySelector('link[data-component="glass-module"]');
    if (existing) return existing.sheet ? Promise.resolve() : new Promise((r) => existing.addEventListener('load', r, { once: true }));
    return new Promise((resolve, reject) => {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = HREF;
        link.dataset.component = 'glass-module';
        link.onload = resolve;
        link.onerror = () => reject(new Error('glass-module: stylesheet failed to load'));
        document.head.appendChild(link);
    });
}
