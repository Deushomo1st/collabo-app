// Every page's styles and code, fetched ahead in the background, so a move to any page shows its shape at once and only the data is left to load.
// Import it on any page (signed in or not: the sign-in pages use it too, so the first move into the app is warm). It starts by itself, after this page has loaded
// and the browser is idle, at low priority, so the page you are on always comes first. It runs on any connection.
import { h } from '/js/services/dom.js';

const PAGES = ['gaze', 'yarnspaces', 'notifications', 'create-post', 'profile', 'profile-settings', 'report',   // what the bar, the fan and the menu lead to
    'view-post', 'connections', 'applications', 'applicants', 'history', 'space', 'wespace', 'workspace'].map((f) => `/HTML-pages/${f}.html`);   // and what you reach from inside them
const LATER = ['/js/services/help.js', '/js/services/help-pages.js'];   // the Help dialog, loaded only when opened

const added = new Set();
const link = (rel, href, extra) => { if (!added.has(href)) { added.add(href); document.head.append(h('link', { rel, href, fetchpriority: 'low', ...extra })); } };

function fetchAhead(href) {   // the page, then the styles and scripts it names, into the browser's cache
    if (href === location.pathname || added.has(href)) return;
    added.add(href);
    fetch(href, { priority: 'low' }).then((r) => (r.ok ? r.text() : '')).then((html) => {
        for (const [, path] of html.matchAll(/(?:href|src)="(\/(?:css|js)\/[^"?]+\.(?:css|js))"/g)) {
            if (path.endsWith('.js')) link('modulepreload', path); else link('prefetch', path, { as: 'style' });
        }
    }).catch(() => {});
}

export function warm() {
    PAGES.forEach(fetchAhead);
    LATER.forEach((path) => link('modulepreload', path));
}

const later = () => (window.requestIdleCallback ? requestIdleCallback(warm, { timeout: 3000 }) : setTimeout(warm, 1500));
if (document.readyState === 'complete') later(); else addEventListener('load', later, { once: true });
