// theme-switcher: dusk/light round button that sets <html data-theme> and remembers the choice.
// Ported from SchoolHub (app.js theme block + style.css .theme-toggle).
// What the themes look like is decided by css/global/theme.css, not by this component.
//
// Usage:
//   import { mountThemeSwitcher, initTheme, getTheme, setTheme }
//       from '/js/components/theme-switcher/theme-switcher.js';
//   initTheme();                           // apply the saved choice (see README for the no-flash snippet)
//   await mountThemeSwitcher('body');      // fixed bottom-right round button
//
// Every switcher on the page stays in sync through the 'collabo:themechange' event.

const BASE = '/js/components/theme-switcher/theme-switcher';
export const THEME_STORAGE_KEY = 'collaboTheme';
export const THEME_EVENT = 'collabo:themechange';

let fragmentPromise = null;

export function getTheme() {
    return document.documentElement.dataset.theme === 'light' ? 'light' : 'dusk';
}

/** remote: also save the pick on the account (every device then uses it); false when adopting the account's own pick. */
export function setTheme(theme, { persist = true, remote = persist } = {}) {
    const t = theme === 'light' ? 'light' : 'dusk';
    document.documentElement.dataset.theme = t;
    if (persist) {
        try { localStorage.setItem(THEME_STORAGE_KEY, t); } catch (e) { /* storage blocked: theme still applies */ }
    }
    if (remote) import('/js/services/api.js').then((m) => m.profileUpdate({ theme: t })).catch(() => {});   // signed out (or offline): it stays on this device
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

// opts: { inline, collapse, plain, onChange(theme) }. Returns { element, destroy }.
// `collapse` hides an inline switcher on small screens, where the theme lives in the page's Settings (see mountThemeRow).
export async function mountThemeSwitcher(target, opts = {}) {
    const host = typeof target === 'string' ? document.querySelector(target) : target;
    if (!host) throw new Error(`theme-switcher: no element matches ${target}`);

    initTheme();
    const tpl = document.createElement('template');
    tpl.innerHTML = (await preloadThemeSwitcher()).trim();
    const root = tpl.content.firstElementChild;
    if (opts.inline) root.classList.add('theme-switcher--inline');
    if (opts.collapse) root.classList.add('theme-switcher--collapse');

    const icon = root.querySelector('.theme-switcher__icon');

    function update() {
        const light = getTheme() === 'light';
        root.classList.toggle('theme-switcher--light', light);
        icon.textContent = light ? '☾' : '☀';   // the icon is the theme a tap gives you
        const next = light ? 'Switch to dark theme' : 'Switch to light theme';
        root.setAttribute('aria-label', next); root.title = next;
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

// A "Theme" row for a Settings dialog: the switcher always shows here, even where the header's is folded away.
export async function mountThemeRow(target) {
    const host = typeof target === 'string' ? document.querySelector(target) : target;
    const row = document.createElement('div');
    row.className = 'theme-switcher__row';
    row.innerHTML = '<span>Theme</span><span></span>';
    host.appendChild(row);
    await mountThemeSwitcher(row.lastChild, { inline: true, plain: true });
}

// A Settings dialog holding just the theme, for pages with no settings of their own.
export async function openThemeSettings() {
    const { openGlassBlurDialog } = await import('/js/components/glass-blur-dialog/glass-blur-dialog.js');
    const { panel } = await openGlassBlurDialog({ size: 'sm', label: 'Settings', html: '<h3 class="glass-blur-dialog__title">Settings</h3><div></div>' });
    await mountThemeRow(panel.lastElementChild);
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
