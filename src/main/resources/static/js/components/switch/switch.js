// switch: an on/off toggle (a real checkbox underneath).
//
// Usage:
//   import { createSwitch, preloadSwitch } from '/js/components/switch/switch.js';
//   await preloadSwitch();                                   // optional: load the CSS early
//   const sw = createSwitch({ checked: true, label: 'Pleas', onChange: (on) => save(on) });
//   host.append(sw.element);
//   sw.input.checked;                                        // read the state; sw.setChecked(false) to set it

const CSS_HREF = '/js/components/switch/switch.css';

export function preloadSwitch() {
    return loadStylesOnce(CSS_HREF, 'switch');
}

// opts: { checked, disabled, label (accessible name), onChange(checked, event) }
// Returns { element, input, setChecked(bool) }.
export function createSwitch(opts = {}) {
    preloadSwitch().catch(() => {});

    const element = document.createElement('span');
    element.className = 'switch';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.className = 'switch__input';
    input.checked = !!opts.checked;
    input.disabled = !!opts.disabled;
    if (opts.label) input.setAttribute('aria-label', opts.label);
    const track = document.createElement('span');
    track.className = 'switch__track';
    element.append(input, track);

    if (opts.onChange) input.addEventListener('change', (e) => opts.onChange(input.checked, e));
    return { element, input, setChecked(on) { input.checked = !!on; } };
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
