// blur-banner: the action-banner without the glow. Same options and the same { element } back; the only effect left is the backdrop blur.
//
// Usage:
//   import { createBlurBanner, preloadBlurBanner } from '/js/components/blur-banner/blur-banner.js';
//   await preloadBlurBanner();                               // optional: load the CSS early
//   const banner = createBlurBanner({
//       title: 'Amaka O. claims ₦30,000 paid to Tolu A.',
//       text: 'Until confirmed this is only a claim.',
//       actions: [{ label: 'Confirm receipt', variant: 'ok', onClick: confirm }],
//   });
//   host.append(banner.element);

const CSS_HREF = '/js/components/blur-banner/blur-banner.css';

export function preloadBlurBanner() {
    return loadStylesOnce(CSS_HREF, 'blur-banner');
}

// opts: { tag ('Action required'), title, text, actions: [{ label, variant: 'ok'|'danger'|undefined, onClick }],
//         note (plain text shown instead of buttons) }
// All text goes in through textContent. Returns { element }.
export function createBlurBanner(opts = {}) {
    preloadBlurBanner().catch(() => {});

    const el = (tag, cls, text) => {
        const node = document.createElement(tag);
        node.className = cls;
        if (text != null) node.textContent = text;
        return node;
    };

    const element = el('div', 'blur-banner');
    element.setAttribute('role', 'alert');
    const body = el('div', 'blur-banner__body');
    body.append(el('span', 'blur-banner__tag', opts.tag ?? 'Action required'));
    if (opts.title) body.append(el('strong', 'blur-banner__title', opts.title));
    if (opts.text) body.append(el('span', 'blur-banner__text', opts.text));

    const actions = el('div', 'blur-banner__actions');
    for (const a of opts.actions ?? []) {
        const btn = el('button', 'blur-banner__btn' + (a.variant ? ` blur-banner__btn--${a.variant}` : ''), a.label);
        btn.type = 'button';
        btn.addEventListener('click', a.onClick);
        actions.append(btn);
    }
    if (opts.note) actions.append(el('span', 'blur-banner__note', opts.note));

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
