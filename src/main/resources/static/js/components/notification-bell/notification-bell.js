// notification-bell: hanging bell with unread badge; opens a glass panel with search + date filter.
// Ported from SchoolHub (app.js notifications block). Depends on glass-blur-dialog and calendar.
//
// Usage:
//   import { mountNotificationBell } from '/js/components/notification-bell/notification-bell.js';
//   const bell = await mountNotificationBell('body', {
//       loadItems: async () => (await fetch('/api/notifications')).json(),
//       onRead:    (n) => fetch(`/api/notifications/${n.id}/read`, { method: 'POST' }),
//       onSelect:  (n) => { /* navigate to whatever n points at */ },
//   });
//
// Item shape: { id, title, body?, createdAt? (ISO string or Date), read? }

import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { openCalendarRange } from '/js/components/calendar/calendar.js';

const BASE = '/js/components/notification-bell/notification-bell';
let partsPromise = null;

export function preloadNotificationBell() {
    if (!partsPromise) {
        partsPromise = Promise.all([
            loadStylesOnce(BASE + '.css', 'notification-bell'),
            fetch(BASE + '.html').then((res) => {
                if (!res.ok) throw new Error(`notification-bell: fragment ${res.status}`);
                return res.text();
            }),
        ]).then(([, html]) => {
            const tpl = document.createElement('template');
            tpl.innerHTML = html;
            return {
                bell: tpl.content.querySelector('.notification-bell'),
                panelHtml: tpl.content.querySelector('template[data-part="panel"]').innerHTML,
            };
        }).catch((err) => {
            partsPromise = null;
            throw err;
        });
    }
    return partsPromise;
}

// opts: { loadItems, onRead(item), onSelect(item), isActionable(item), filters [{ id, label, match(item) }], pollMs (default 60000, 0 = off), inline }
// Returns { refresh, open, destroy, element }.
export async function mountNotificationBell(target, opts = {}) {
    const host = typeof target === 'string' ? document.querySelector(target) : target;
    if (!host) throw new Error(`notification-bell: no element matches ${target}`);

    const parts = await preloadNotificationBell();
    const root = parts.bell.cloneNode(true);
    if (opts.inline) root.classList.add('notification-bell--inline');

    const button = root.querySelector('.notification-bell__button');
    const badge = root.querySelector('.notification-bell__badge');
    const loadItems = opts.loadItems || (async () => []);
    const isActionable = opts.isActionable || (() => false);

    let items = [];
    let lastUnread = 0;
    let pollTimer = null;
    let rerenderPanel = null;          // set while the panel is open

    function replay(el, cls) {
        el.classList.remove(cls);
        void el.offsetWidth;           // restart the animation
        el.classList.add(cls);
    }
    button.addEventListener('animationend', () => {
        button.classList.remove('notification-bell__button--swinging', 'notification-bell__button--ringing');
    });

    function updateBadge() {
        const unread = items.filter((n) => !n.read).length;
        badge.hidden = unread === 0;
        badge.textContent = unread > 99 ? '99+' : String(unread);
        button.setAttribute('aria-label', unread ? `Notifications, ${unread} unread` : 'Notifications');
        if (unread > lastUnread) replay(button, 'notification-bell__button--ringing');
        lastUnread = unread;
    }

    async function refresh() {
        items = (await loadItems()) || [];
        updateBadge();
        if (rerenderPanel) rerenderPanel();
        return items;
    }

    async function open() {
        replay(button, 'notification-bell__button--swinging');
        const dlg = await openGlassBlurDialog({
            className: 'notification-bell__panel',
            html: parts.panelHtml,
            onClose: () => { rerenderPanel = null; },
        });
        const list = dlg.panel.querySelector('.notification-bell__list');
        const search = dlg.panel.querySelector('.notification-bell__search');
        const filterBtn = dlg.panel.querySelector('.notification-bell__filter');
        let query = '';
        let filter = null;              // the chosen entry of opts.filters, if any
        let from = null;
        let to = null;

        function render() {
            const shown = items.filter((n) => {
                if (query && !`${n.title || ''} ${n.body || ''}`.toLowerCase().includes(query)) return false;
                if (filter && filter.match && !filter.match(n)) return false;
                const day = dayOf(n.createdAt);
                if (from && (!day || day < from)) return false;
                if (to && (!day || day > to)) return false;
                return true;
            });
            if (!shown.length) {
                list.innerHTML = `<p class="notification-bell__empty">${items.length ? 'No matches.' : 'No notifications yet.'}</p>`;
                return;
            }
            list.replaceChildren(...shown.map((n) => {
                const b = document.createElement('button');
                b.type = 'button';
                b.className = 'notification-bell__item'
                    + (n.read ? '' : ' notification-bell__item--unread')
                    + (isActionable(n) ? ' notification-bell__item--actionable' : '');
                b.innerHTML = '<span class="notification-bell__item-title"></span>'
                    + '<span class="notification-bell__item-body"></span>'
                    + '<span class="notification-bell__item-time"></span>';
                b.children[0].textContent = n.title || '';
                b.children[1].textContent = n.body || '';
                b.children[2].textContent = n.createdAt ? new Date(n.createdAt).toLocaleDateString() : '';
                b.addEventListener('click', () => {
                    if (!n.read) {
                        n.read = true;
                        updateBadge();
                        if (typeof opts.onRead === 'function') opts.onRead(n);
                    }
                    dlg.close();
                    if (typeof opts.onSelect === 'function') opts.onSelect(n);
                });
                return b;
            }));
        }
        rerenderPanel = render;

        if (opts.filters && opts.filters.length) {           // chips: { id, label, match(item) }; the first one starts selected
            filter = opts.filters[0];
            const chips = document.createElement('div');
            chips.className = 'notification-bell__chips';
            chips.setAttribute('role', 'group');
            chips.setAttribute('aria-label', 'Notification type');
            for (const f of opts.filters) {
                const c = document.createElement('button');
                c.type = 'button';
                c.className = 'notification-bell__chip';
                c.textContent = f.label;
                c.setAttribute('aria-pressed', String(f === filter));
                c.addEventListener('click', () => {
                    filter = f;
                    chips.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', String(b === c)));
                    render();
                });
                chips.append(c);
            }
            list.before(chips);
        }

        search.addEventListener('input', (e) => { query = e.target.value.trim().toLowerCase(); render(); });
        filterBtn.addEventListener('click', () => {
            openCalendarRange({
                from, to,
                onApply: (f, t) => {
                    from = f;
                    to = t;
                    const on = !!(f || t);
                    filterBtn.classList.toggle('notification-bell__filter--active', on);
                    filterBtn.setAttribute('aria-pressed', String(on));
                    render();
                },
            });
        });

        render();                                   // show what we already have
        refresh().catch(() => {
            if (!items.length) list.innerHTML = '<p class="notification-bell__empty">Could not load notifications.</p>';
        });
        return dlg;
    }

    button.addEventListener('click', () => { open(); });

    const pollMs = opts.pollMs ?? 60000;
    refresh().catch(() => {});
    if (pollMs > 0) pollTimer = setInterval(() => refresh().catch(() => {}), pollMs);

    function destroy() {
        clearInterval(pollTimer);
        root.remove();
    }

    host.appendChild(root);
    return { refresh, open, destroy, element: root };
}

// 'YYYY-MM-DD' for filtering. Strings are sliced as-is (so ISO timestamps filter by their UTC day);
// Date objects use the local day.
function dayOf(v) {
    if (!v) return '';
    if (v instanceof Date) {
        return `${v.getFullYear()}-${String(v.getMonth() + 1).padStart(2, '0')}-${String(v.getDate()).padStart(2, '0')}`;
    }
    return String(v).slice(0, 10);
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
