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

// opts: { inline, plain, onChange(theme) }. Returns { element, destroy }.
// An inline switcher (a page header's) turns into a gear button on small screens: the gear opens a Settings dialog that holds
// the switcher, so the header keeps its room. `plain` is the switcher inside that dialog, which stays put at every size.
export async function mountThemeSwitcher(target, opts = {}) {
    const host = typeof target === 'string' ? document.querySelector(target) : target;
    if (!host) throw new Error(`theme-switcher: no element matches ${target}`);

    initTheme();
    const tpl = document.createElement('template');
    tpl.innerHTML = (await preloadThemeSwitcher()).trim();
    const root = tpl.content.firstElementChild;
    if (opts.inline) root.classList.add('theme-switcher--inline');
    if (opts.plain) root.classList.add('theme-switcher--plain');

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

    let gear = null;
    if (opts.inline && !opts.plain) {
        gear = document.createElement('button');
        gear.type = 'button';
        gear.className = 'theme-switcher__gear';
        gear.setAttribute('aria-label', 'Settings');
        gear.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>';
        gear.addEventListener('click', openSettings);
        host.appendChild(gear);
    }

    return {
        element: root,
        destroy() {
            document.removeEventListener(THEME_EVENT, onThemeChange);
            gear?.remove();
            root.remove();
        },
    };
}

// The small-screen Settings dialog. The dialog component is loaded only now, so the switcher itself stays standalone.
async function openSettings() {
    const { openGlassBlurDialog } = await import('/js/components/glass-blur-dialog/glass-blur-dialog.js');
    const { panel } = await openGlassBlurDialog({ size: 'sm', label: 'Settings', html:
        '<h3 class="glass-blur-dialog__title">Settings</h3><div class="theme-switcher__row"><span>Theme</span><span class="theme-switcher__slot"></span></div>' });
    await mountThemeSwitcher(panel.querySelector('.theme-switcher__slot'), { inline: true, plain: true });
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
