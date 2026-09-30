// action-banner: a glowing "action required" strip with a title, a line of text and buttons.
// Use it for anything that blocks something until the user answers (a payment claim, a response clock).
//
// Usage:
//   import { createActionBanner, preloadActionBanner } from '/js/components/action-banner/action-banner.js';
//   await preloadActionBanner();                             // optional: load the CSS early
//   const banner = createActionBanner({
//       title: 'Amaka O. claims ₦30,000 paid to Tolu A.',
//       text: 'Until confirmed this is only a claim.',
//       actions: [{ label: 'Confirm receipt', variant: 'ok', onClick: confirm }],
//   });
//   host.append(banner.element);

const CSS_HREF = '/js/components/action-banner/action-banner.css';

export function preloadActionBanner() {
    return loadStylesOnce(CSS_HREF, 'action-banner');
}

// opts: { tag ('Action required'), title, text, actions: [{ label, variant: 'ok'|'danger'|undefined, onClick }],
//         note (plain text shown instead of buttons, e.g. "Waiting on the recipient") }
// All text goes in through textContent. Returns { element }.
export function createActionBanner(opts = {}) {
    preloadActionBanner().catch(() => {});

    const el = (tag, cls, text) => {
        const node = document.createElement(tag);
        node.className = cls;
        if (text != null) node.textContent = text;
        return node;
    };

    const element = el('div', 'action-banner');
    element.setAttribute('role', 'alert');
    const body = el('div', 'action-banner__body');
    body.append(el('span', 'action-banner__tag', opts.tag ?? 'Action required'));
    if (opts.title) body.append(el('strong', 'action-banner__title', opts.title));
    if (opts.text) body.append(el('span', 'action-banner__text', opts.text));

    const actions = el('div', 'action-banner__actions');
    for (const a of opts.actions ?? []) {
        const btn = el('button', 'action-banner__btn' + (a.variant ? ` action-banner__btn--${a.variant}` : ''), a.label);
        btn.type = 'button';
        btn.addEventListener('click', a.onClick);
        actions.append(btn);
    }
    if (opts.note) actions.append(el('span', 'action-banner__note', opts.note));

    element.append(body);
    if (actions.childElementCount) element.append(actions);
    return { element };
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
