// Small shared pieces for the admin console: notices, dialogs, relative times. Text goes in through textContent only.
import { h } from '/js/services/dom.js';

export function notify(text, isError = false) {
    const box = document.getElementById('notices');
    const n = h('div', { class: `ad-notice${isError ? ' is-error' : ''}`, role: 'status', text });
    box.append(n);
    setTimeout(() => n.remove(), isError ? 6000 : 3000);
}

/** A modal on the native <dialog> (focus trap, Escape to close). Resolves to whatever `done(value)` is called with, or null when dismissed. */
function modal({ title, lead, fields = [], submit, danger = false, valid = () => true }) {
    return new Promise((resolve) => {
        const dlg = h('dialog', { class: 'ad-dialog' });
        const err = h('p', { class: 'ad-dialog__err', hidden: true });
        const go = h('button', { class: `ad-btn ${danger ? 'ad-btn--danger' : 'ad-btn--primary'}`, type: 'submit', text: submit });
        const form = h('form', { method: 'dialog' },
            h('h3', { text: title }), lead && h('p', { class: 'ad-dialog__lead', text: lead }), ...fields.map((f) => f.row), err,
            h('div', { class: 'ad-dialog__actions' }, h('button', { class: 'ad-btn', type: 'button', text: 'Cancel', onclick: () => dlg.close() }), go));
        const refresh = () => { go.disabled = !valid(); };
        form.addEventListener('input', refresh);
        refresh();
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            if (!valid()) return;
            resolve(Object.fromEntries(fields.map((f) => [f.name, f.input.type === 'checkbox' ? f.input.checked : f.input.value])));
            dlg.dataset.done = '1';
            dlg.close();
        });
        dlg.addEventListener('close', () => { if (!dlg.dataset.done) resolve(null); dlg.remove(); });
        dlg.append(form);
        document.body.append(dlg);
        dlg.showModal();
        (fields[0]?.input || go).focus();
    });
}

export const field = (name, label, attrs = {}) => {
    const input = h(attrs.tag || 'input', { class: 'ad-input', autocomplete: 'off', spellcheck: 'false', ...attrs, tag: null });
    return { name, input, row: h('label', { class: 'ad-field' }, h('span', { text: label }), input) };
};

/** Yes/no. Resolves true or false. */
export const confirmDialog = (opts) => modal({ title: opts.title, lead: opts.text, submit: opts.confirm || 'Confirm', danger: opts.danger }).then(Boolean);

/** The destructive kind: the button stays off until the exact word is typed. */
export async function typedConfirm({ title, text, expected, confirm = 'Delete' }) {
    const f = field('typed', `Type "${expected}" to confirm`);
    return Boolean(await modal({ title, lead: text, fields: [f], submit: confirm, danger: true, valid: () => f.input.value === expected }));
}

/** One or more fields; resolves to { name: value } or null. */
export const formDialog = (opts) => modal(opts);

export function ago(iso) {
    const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 60) return 'just now';
    if (s < 3600) return `${Math.floor(s / 60)} min ago`;
    if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function duration(seconds) {
    const d = Math.floor(seconds / 86400), hrs = Math.floor((seconds % 86400) / 3600), m = Math.floor((seconds % 3600) / 60);
    return d > 0 ? `${d}d ${hrs}h` : hrs > 0 ? `${hrs}h ${m}m` : `${m}m`;
}

export async function copy(text) {
    try { await navigator.clipboard.writeText(text); notify('Copied.'); }
    catch { notify('Could not copy. Select the text and copy it by hand.', true); }
}
