// glass-blur-dialog: layered, auto-stacking glass dialogs.
// Ported from SchoolHub app/modal (openGlassModal / glassConfirm / glassAlert).
//
// Usage:
//   import { openGlassBlurDialog, glassBlurConfirm, glassBlurAlert, preloadGlassBlurDialog }
//       from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
//
//   const dlg = await openGlassBlurDialog({ html: '<h2 class="glass-blur-dialog__title">Hi</h2>', size: 'sm' });
//   dlg.close();
//   if (await glassBlurConfirm('Delete this file?', { danger: true, okText: 'Delete' })) { ... }
//   await glassBlurAlert('Saved.');
//
// openGlassBlurDialog(opts):
//   html         string   panel content (NOT escaped: never pass user input unescaped)
//   size         'sm' | 'md' (default, fits content) | 'lg' | 'xl'
//   className    string   extra classes on the panel
//   label        string   accessible name, if the content has no .glass-blur-dialog__title
//   clear        boolean  see-through glass; honoured for the first dialog only
//   dismissable  boolean  default true; false disables backdrop click AND Escape
//   onClose      function called once when the dialog closes
//
// Stacking: dialogs 1 and 2 frost the page; dialog 3+ uses the deepest frost.
// Call preloadGlassBlurDialog() at idle so the first open doesn't wait on the network.

const BASE = '/js/components/glass-blur-dialog/glass-blur-dialog';
const FOCUSABLE = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

const stack = [];

// While a dialog is open the page behind it stays put: no scrolling it by wheel or by touch (a tap outside still closes the dialog).
const lock = (on) => {
    const de = document.documentElement;
    if (on) { de.style.setProperty('--gbd-sbw', `${innerWidth - de.clientWidth}px`); de.classList.add('gbd-lock'); }   // the scrollbar's width is kept so the page does not jump sideways
    else de.classList.remove('gbd-lock');
};
let shellPromise = null;
let uid = 0;

export function preloadGlassBlurDialog() {
    if (!shellPromise) {
        shellPromise = Promise.all([
            loadStylesOnce(BASE + '.css', 'glass-blur-dialog'),
            fetch(BASE + '.html').then((res) => {
                if (!res.ok) throw new Error(`glass-blur-dialog: fragment ${res.status}`);
                return res.text();
            }),
        ]).then(([, html]) => {
            const tpl = document.createElement('template');
            tpl.innerHTML = html.trim();
            return tpl;
        }).catch((err) => {
            shellPromise = null;          // allow a retry on the next open
            throw err;
        });
    }
    return shellPromise;
}

export async function openGlassBlurDialog(opts = {}) {
    const shell = await preloadGlassBlurDialog();

    const depth = stack.length;       // 0 = first dialog
    let frost = depth >= 2 ? 'frost-2' : 'frost';
    if (opts.clear && depth === 0) frost = null;

    const root = shell.content.firstElementChild.cloneNode(true);
    const panel = root.querySelector('.glass-blur-dialog__panel');
    if (frost) root.classList.add(`glass-blur-dialog--${frost}`);
    if (opts.size && opts.size !== 'md') panel.classList.add(`glass-blur-dialog__panel--${opts.size}`);
    if (opts.className) panel.classList.add(...opts.className.split(/\s+/).filter(Boolean));
    panel.innerHTML = opts.html || '';

    const title = panel.querySelector('.glass-blur-dialog__title');
    if (opts.label) {
        panel.setAttribute('aria-label', opts.label);
    } else if (title) {
        title.id = title.id || `glass-blur-dialog-title-${++uid}`;
        panel.setAttribute('aria-labelledby', title.id);
    }

    const previousFocus = document.activeElement;
    const dismissable = opts.dismissable !== false;
    let closed = false;

    function close() {
        if (closed) return;
        closed = true;
        const i = stack.indexOf(entry);
        if (i >= 0) stack.splice(i, 1);
        if (!stack.length) lock(false);
        root.classList.remove('glass-blur-dialog--open');
        root.classList.add('glass-blur-dialog--closing');
        setTimeout(() => root.remove(), 180);
        if (previousFocus && typeof previousFocus.focus === 'function') previousFocus.focus();
        if (typeof opts.onClose === 'function') opts.onClose();
    }

    if (dismissable) {   // a round X on the panel's top edge, on every dialog that can be dismissed (Escape and a tap outside close it too)
        const x = document.createElement('button');
        x.type = 'button'; x.className = 'glass-blur-dialog__x'; x.tabIndex = -1; x.setAttribute('aria-label', 'Close');
        x.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>';
        x.addEventListener('click', close);
        panel.parentElement.append(x);
    }
    const entry = { panel, close, dismissable };
    if (!stack.length) lock(true);
    stack.push(entry);
    root.addEventListener('touchmove', (e) => { if (!panel.contains(e.target)) e.preventDefault(); }, { passive: false });   // a drag on the dim layer would scroll the page behind

    if (dismissable) {
        root.addEventListener('click', (e) => { if (e.target === root) close(); });
    }

    document.body.appendChild(root);
    requestAnimationFrame(() => {
        root.classList.add('glass-blur-dialog--open');
        (panel.querySelector(FOCUSABLE) || panel).focus();
    });

    return { close, panel };
}

export async function glassBlurConfirm(message, opts = {}) {
    let settle;
    const answer = new Promise((resolve) => {
        let done = false;
        settle = (value) => { if (!done) { done = true; resolve(value); } };
    });

    const dlg = await openGlassBlurDialog({
        clear: opts.clear,
        dismissable: opts.dismissable,
        html: `<h2 class="glass-blur-dialog__title">${esc(opts.title ?? 'Are you sure?')}</h2>`
            + `<p class="glass-blur-dialog__text">${esc(message)}</p>`
            + '<div class="glass-blur-dialog__actions">'
            + `<button type="button" class="glass-blur-dialog__btn glass-blur-dialog__btn--ghost" data-action="cancel">${esc(opts.cancelText ?? 'Cancel')}</button>`
            + `<button type="button" class="glass-blur-dialog__btn${opts.danger ? ' glass-blur-dialog__btn--danger' : ''}" data-action="ok">${esc(opts.okText ?? 'Confirm')}</button>`
            + '</div>',
        onClose: () => settle(false),
    });

    dlg.panel.querySelector('[data-action="cancel"]').addEventListener('click', () => { settle(false); dlg.close(); });
    dlg.panel.querySelector('[data-action="ok"]').addEventListener('click', () => { settle(true); dlg.close(); });
    return answer;
}

export async function glassBlurAlert(message, opts = {}) {
    let settle;
    const done = new Promise((resolve) => { settle = resolve; });

    const dlg = await openGlassBlurDialog({
        clear: opts.clear,
        html: `<h2 class="glass-blur-dialog__title">${esc(opts.title ?? 'Notice')}</h2>`
            + `<p class="glass-blur-dialog__text glass-blur-dialog__text--pre">${esc(message)}</p>`
            + '<div class="glass-blur-dialog__actions">'
            + `<button type="button" class="glass-blur-dialog__btn" data-action="ok">${esc(opts.okText ?? 'OK')}</button>`
            + '</div>',
        onClose: () => settle(),
    });

    dlg.panel.querySelector('[data-action="ok"]').addEventListener('click', () => dlg.close());
    return done;
}

// Escape closes the topmost dialog; Tab stays inside it.
document.addEventListener('keydown', (e) => {
    const top = stack[stack.length - 1];
    if (!top) return;
    if (e.key === 'Escape' && top.dismissable) {
        top.close();
    } else if (e.key === 'Tab') {
        trapFocus(e, top.panel);
    }
});

function trapFocus(event, container) {
    const items = [...container.querySelectorAll(FOCUSABLE)];
    if (items.length === 0) {
        event.preventDefault();
        container.focus();
        return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    const active = document.activeElement;

    if (!container.contains(active)) {
        event.preventDefault();
        first.focus();
    } else if (event.shiftKey && (active === first || active === container)) {
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

function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => (
        { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
}
