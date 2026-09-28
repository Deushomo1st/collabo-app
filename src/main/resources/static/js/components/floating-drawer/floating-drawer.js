// floating-drawer: macOS-style drawer hanging from the top-left corner.
// Ported from SchoolHub app/drawer (drawer.js + the nav wiring in dashboards.js).
//
// Usage:
//   import { mountFloatingDrawer } from '/js/components/floating-drawer/floating-drawer.js';
//   const drawer = await mountFloatingDrawer('body', {
//       items:  [{ id: 'home', label: 'Home', icon: '<svg …>' }, …],
//       footer: [{ id: 'logout', label: 'Sign out', icon: '<svg …>' }],
//       activeId: 'home',
//       onSelect: (item) => { … },
//   });
//
// Stages: 0 closed · 1 open (hover peek, icons only) · 2 expanded (clicked, labels shown).
// Icons are SVG markup strings (no icon library needed).

const BASE = '/js/components/floating-drawer/floating-drawer';
const DEFAULT_TRIGGER_ICON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z"/><path d="M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12"/><path d="M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17"/></svg>';

let fragmentPromise = null;

export function preloadFloatingDrawer() {
    if (!fragmentPromise) {
        fragmentPromise = Promise.all([
            loadStylesOnce(BASE + '.css', 'floating-drawer'),
            fetch(BASE + '.html').then((res) => {
                if (!res.ok) throw new Error(`floating-drawer: fragment ${res.status}`);
                return res.text();
            }),
        ]).then(([, html]) => html).catch((err) => {
            fragmentPromise = null;
            throw err;
        });
    }
    return fragmentPromise;
}

// opts: { items, footer, activeId, onSelect(item), triggerIcon, mirrorActiveIcon (default true), inline }
// Returns { setItems, setFooter, setActive, dismiss, destroy, element }.
export async function mountFloatingDrawer(target, opts = {}) {
    const host = typeof target === 'string' ? document.querySelector(target) : target;
    if (!host) throw new Error(`floating-drawer: no element matches ${target}`);

    const tpl = document.createElement('template');
    tpl.innerHTML = (await preloadFloatingDrawer()).trim();
    const root = tpl.content.firstElementChild;
    if (opts.inline) root.classList.add('floating-drawer--inline');

    const trigger = root.querySelector('.floating-drawer__trigger');
    const body = root.querySelector('.floating-drawer__body');
    const itemsEl = root.querySelector('.floating-drawer__items');
    const footerEl = root.querySelector('.floating-drawer__footer');
    const defaultIcon = opts.triggerIcon || DEFAULT_TRIGGER_ICON;
    const mirror = opts.mirrorActiveIcon !== false;

    let items = [];
    let activeId = opts.activeId ?? null;
    let stage = 0;
    let timer = null;
    let clickOpened = false;

    function makeButton(item, isFooter) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'floating-drawer__item' + (isFooter ? ' floating-drawer__item--danger' : '');
        b.dataset.id = item.id;
        b.innerHTML = `${item.icon || ''}<span class="floating-drawer__label"></span>`;
        b.querySelector('.floating-drawer__label').textContent = item.label;
        b.addEventListener('click', () => select(item, isFooter));
        return b;
    }

    function select(item, isFooter) {
        if (!isFooter) setActive(item.id);
        dismiss();
        if (typeof opts.onSelect === 'function') opts.onSelect(item);
    }

    function setActive(id) {
        activeId = id;
        itemsEl.querySelectorAll('.floating-drawer__item').forEach((b) => {
            const on = b.dataset.id === String(id);
            b.classList.toggle('floating-drawer__item--active', on);
            if (on) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
        });
        const active = items.find((i) => String(i.id) === String(id));
        trigger.innerHTML = (mirror && active && active.icon) || defaultIcon;
    }

    function setItems(next) {
        items = next || [];
        itemsEl.replaceChildren(...items.map((i) => makeButton(i, false)));
        setActive(activeId);
        dismiss();
    }

    function setFooter(next) {
        footerEl.replaceChildren(...(next || []).map((i) => makeButton(i, true)));
    }

    function measureExpandedWidth() {
        let max = 0;
        root.querySelectorAll('.floating-drawer__label').forEach((s) => { max = Math.max(max, s.scrollWidth); });
        return Math.max(116, 20 + 10 + max + 28);    // icon + gap + label + padding
    }

    function setStage(s) {
        stage = s;
        root.classList.toggle('floating-drawer--open', s >= 1);
        root.classList.toggle('floating-drawer--expanded', s >= 2);
        root.style.width = s >= 2 ? measureExpandedWidth() + 'px' : '';
        trigger.setAttribute('aria-expanded', String(s >= 1));
        body.inert = s === 0;                        // hidden items can't be tabbed into
    }

    function dismiss() {
        clearTimeout(timer);
        clickOpened = false;
        setStage(0);
    }

    root.addEventListener('mouseenter', () => {
        clearTimeout(timer);
        if (!clickOpened && stage === 0) setStage(1);
    });
    root.addEventListener('mouseleave', () => {
        if (clickOpened) return;
        if (stage >= 2) {
            setStage(1);
            timer = setTimeout(() => setStage(0), 2000);
        } else {
            timer = setTimeout(() => setStage(0), 150);
        }
    });
    trigger.addEventListener('click', (e) => {
        e.stopPropagation();
        clearTimeout(timer);
        if (clickOpened) {
            dismiss();
        } else {
            clickOpened = true;
            setStage(2);
        }
    });

    function onDocClick(e) { if (stage > 0 && !root.contains(e.target)) dismiss(); }
    function onKeyDown(e) {
        if (e.key === 'Escape' && stage > 0) {
            const hadFocus = root.contains(document.activeElement);
            dismiss();
            if (hadFocus) trigger.focus();
        }
    }
    document.addEventListener('click', onDocClick);
    document.addEventListener('keydown', onKeyDown);

    function destroy() {
        clearTimeout(timer);
        document.removeEventListener('click', onDocClick);
        document.removeEventListener('keydown', onKeyDown);
        root.remove();
    }

    setItems(opts.items);
    setFooter(opts.footer);
    host.appendChild(root);

    return { setItems, setFooter, setActive, dismiss, destroy, element: root };
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
