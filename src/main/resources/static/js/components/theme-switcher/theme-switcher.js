// theme-switcher: dusk/light toggle that sets <html data-theme> and remembers the choice.
// Ported from SchoolHub (app.js theme block + style.css .theme-toggle).
// What the themes look like is decided by css/global/theme.css, not by this component.
//
// Usage:
//   import { mountThemeSwitcher, initTheme, getTheme, setTheme }
//       from '/js/components/theme-switcher/theme-switcher.js';
//   initTheme();                           // apply the saved choice (see README for the no-flash snippet)
//   await mountThemeSwitcher('body');      // fixed bottom-right pill
//
// Every switcher on the page stays in sync through the 'collabo:themechange' event.

const BASE = '/js/components/theme-switcher/theme-switcher';
export const THEME_STORAGE_KEY = 'collaboTheme';
export const THEME_EVENT = 'collabo:themechange';

let fragmentPromise = null;

export function getTheme() {
    return document.documentElement.dataset.theme === 'light' ? 'light' : 'dusk';
}

export function setTheme(theme, { persist = true } = {}) {
    const t = theme === 'light' ? 'light' : 'dusk';
    document.documentElement.dataset.theme = t;
    if (persist) {
        try { localStorage.setItem(THEME_STORAGE_KEY, t); } catch (e) { /* storage blocked: theme still applies */ }
    }
    document.dispatchEvent(new CustomEvent(THEME_EVENT, { detail: { theme: t } }));
    return t;
}

// Applies the saved theme if the page hasn't set one already. Default: dusk.
export function initTheme() {
    if (!document.documentElement.dataset.theme) {
        let saved = null;
        try { saved = localStorage.getItem(THEME_STORAGE_KEY); } catch (e) { /* ignore */ }
        if (saved) document.documentElement.dataset.theme = saved === 'light' ? 'light' : 'dusk';
    }
    return getTheme();
}

export function preloadThemeSwitcher() {
    if (!fragmentPromise) {
        fragmentPromise = Promise.all([
            loadStylesOnce(BASE + '.css', 'theme-switcher'),
            fetch(BASE + '.html').then((res) => {
                if (!res.ok) throw new Error(`theme-switcher: fragment ${res.status}`);
                return res.text();
            }),
        ]).then(([, html]) => html).catch((err) => {
            fragmentPromise = null;
            throw err;
        });
    }
    return fragmentPromise;
}

// opts: { inline, onChange(theme) }. Returns { element, destroy }.
export async function mountThemeSwitcher(target, opts = {}) {
    const host = typeof target === 'string' ? document.querySelector(target) : target;
    if (!host) throw new Error(`theme-switcher: no element matches ${target}`);

    initTheme();
    const tpl = document.createElement('template');
    tpl.innerHTML = (await preloadThemeSwitcher()).trim();
    const root = tpl.content.firstElementChild;
    if (opts.inline) root.classList.add('theme-switcher--inline');

    const label = root.querySelector('.theme-switcher__label');
    const icon = root.querySelector('.theme-switcher__icon');

    function update() {
        const light = getTheme() === 'light';
        root.classList.toggle('theme-switcher--light', light);
        label.textContent = light ? 'Light' : 'Dark';
        icon.textContent = light ? '☀' : '☾';
        root.setAttribute('aria-label', light ? 'Switch to dark theme' : 'Switch to light theme');
    }

    function onThemeChange(e) {
        update();
        if (typeof opts.onChange === 'function') opts.onChange(e.detail.theme);
    }

    root.addEventListener('click', () => setTheme(getTheme() === 'light' ? 'dusk' : 'light'));
    document.addEventListener(THEME_EVENT, onThemeChange);
    update();
    host.appendChild(root);

    return {
        element: root,
        destroy() {
            document.removeEventListener(THEME_EVENT, onThemeChange);
            root.remove();
        },
    };
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
