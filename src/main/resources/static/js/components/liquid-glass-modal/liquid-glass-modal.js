// liquid-glass-modal: a modal whose backdrop refracts the page (SVG feDisplacementMap).
// Vanilla port of a Lovable React/Tailwind v4 study.
//
// Usage:
//   import { mountLiquidGlassModal, preloadLiquidGlassModal }
//       from '/js/components/liquid-glass-modal/liquid-glass-modal.js';
//   preloadLiquidGlassModal();                               // optional: first open is instant
//   const modal = await mountLiquidGlassModal('#host', {
//       trigger: button,                                     // focus returns here on close
//       meta: 'Release / 02', title: 'New workspace',
//       body: 'Your team space is ready.',                   // text (escaped)  -- or --  html: '<p>…</p>' (NOT escaped)
//       footer: 'Refraction active',
//   });
//   modal.close();
//
// Each call opens one modal. Escape closes the topmost one.

const BASE = '/js/components/liquid-glass-modal/liquid-glass-modal';
const FILTER_ID = 'liquid-glass-modal-filter';
const FOCUSABLE = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

const stack = [];          // open modals, topmost last
let fragmentPromise = null;
let uid = 0;

export function preloadLiquidGlassModal() {
    if (!fragmentPromise) {
        fragmentPromise = Promise.all([
            loadStylesOnce(BASE + '.css', 'liquid-glass-modal'),
            fetch(BASE + '.html').then((res) => {
                if (!res.ok) throw new Error(`liquid-glass-modal: fragment ${res.status}`);
                return res.text();
            }),
        ]).then(([, html]) => {
            const tpl = document.createElement('template');
            tpl.innerHTML = html;
            return {
                portal: tpl.content.querySelector('.liquid-glass-modal__portal'),
                filter: tpl.content.querySelector('.liquid-glass-modal__filter'),
            };
        }).catch((err) => {
            fragmentPromise = null;          // allow a retry
            throw err;
        });
    }
    return fragmentPromise;
}

// target:  selector or element to append the portal to (it covers the viewport either way).
// options: { trigger, meta, title, body, html, footer, onMount(modalEl), onClose() }
// Returns { element, close, destroy }, or undefined if the target doesn't exist.
export async function mountLiquidGlassModal(target = '#liquid-glass-modal', options = {}) {
    const host = typeof target === 'string' ? document.querySelector(target) : target;
    if (!host) return undefined;

    const parts = await preloadLiquidGlassModal();
    const portal = parts.portal.cloneNode(true);
    const modal = portal.querySelector('.liquid-glass-modal');
    const backdrop = portal.querySelector('.liquid-glass-modal__backdrop');
    const closeBtn = portal.querySelector('.liquid-glass-modal__close');
    const title = portal.querySelector('.liquid-glass-modal__title');
    const body = portal.querySelector('.liquid-glass-modal__body');

    // Content: options override the fragment's defaults.
    if (options.meta != null) portal.querySelector('.liquid-glass-modal__meta').textContent = options.meta;
    if (options.title != null) title.textContent = options.title;
    if (options.html != null) body.innerHTML = options.html;
    else if (options.body != null) body.textContent = options.body;
    if (options.footer != null) portal.querySelector('.liquid-glass-modal__footer-text').textContent = options.footer;

    // Unique ids per open, so repeat or stacked opens stay valid.
    const n = ++uid;
    title.id = `liquid-glass-modal-title-${n}`;
    body.id = `liquid-glass-modal-body-${n}`;
    modal.setAttribute('aria-labelledby', title.id);
    modal.setAttribute('aria-describedby', body.id);

    // One shared SVG filter per document (its id is referenced from the CSS).
    if (!document.getElementById(FILTER_ID)) document.body.appendChild(parts.filter.cloneNode(true));

    const previousOverflow = document.body.style.overflow;
    const previousFocus = document.activeElement;
    let closed = false;

    // Cleanup that must run however the modal disappears.
    function finish() {
        if (closed) return;
        closed = true;
        observer.disconnect();
        const i = stack.indexOf(entry);
        if (i >= 0) stack.splice(i, 1);
        if (stack.length === 0) document.body.style.overflow = previousOverflow;
    }

    function close() {
        if (closed) return;
        portal.remove();
        finish();
        if (typeof options.onClose === 'function') options.onClose();
        const back = options.trigger || previousFocus;
        if (back && typeof back.focus === 'function') back.focus();
    }

    const entry = { modal, close };
    stack.push(entry);

    backdrop.addEventListener('click', close);
    closeBtn.addEventListener('click', close);

    // If a page removes the modal itself (e.g. clears its container), still clean up.
    const observer = new MutationObserver(() => { if (!portal.isConnected) finish(); });
    observer.observe(document.body, { childList: true, subtree: true });

    host.appendChild(portal);
    document.body.style.overflow = 'hidden';
    closeBtn.focus();

    if (typeof options.onMount === 'function') options.onMount(modal);
    return { element: modal, close, destroy: close };
}

// Escape closes the topmost modal; Tab stays inside it.
document.addEventListener('keydown', (e) => {
    const top = stack[stack.length - 1];
    if (!top) return;
    if (e.key === 'Escape') top.close();
    else if (e.key === 'Tab') trapFocus(e, top.modal);
});

function trapFocus(event, container) {
    const items = [...container.querySelectorAll(FOCUSABLE)];
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    const active = document.activeElement;
    if (!container.contains(active)) {
        event.preventDefault();
        first.focus();
    } else if (event.shiftKey && active === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
    }
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
