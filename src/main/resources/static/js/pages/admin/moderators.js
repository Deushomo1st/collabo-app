// Moderators: separate accounts the admin creates. They never appear under Users and cannot sign in as users.
import { h } from '/js/services/dom.js';
import * as api from '/js/services/admin-api.js';
import { notify, field, confirmDialog, formDialog, ago, copy } from './ui.js';

const ALPHABET = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** 16 characters without look-alikes (no 0/O, 1/l/I), from the browser's own random source. */
function generate() {
    const pick = new Uint32Array(16);
    crypto.getRandomValues(pick);
    return Array.from(pick, (n) => ALPHABET[n % ALPHABET.length]).join('');
}

/** Dialog field plus a button that fills it with a generated password, shown in plain text so it can be handed over. */
function passwordField(label) {
    const f = field('password', label, { type: 'text', autocomplete: 'off' });
    const make = h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: 'Generate', onclick: () => { f.input.value = generate(); f.input.dispatchEvent(new Event('input', { bubbles: true })); } });
    f.row.append(make);
    return f;
}

export async function moderatorsView() {
    const state = { all: await api.moderators() };
    const body = h('tbody');

    async function act(work, done) {
        try { await work(); notify(done); state.all = await api.moderators(); draw(); }
        catch (e) { if (e instanceof api.AdminAuthError) throw e; notify(e.message, true); }
    }

    async function reset(m) {
        const pw = passwordField('New password (10+ characters)');
        const out = await formDialog({ title: `New password for ${m.name}`, lead: 'Share it with them yourself. They can change it after signing in.', fields: [pw], submit: 'Set password',
            valid: () => pw.input.value.length >= 10 });
        if (out) await act(() => api.resetModeratorPassword(m.id, out.password), 'Password reset.');
    }

    function row(m) {
        return h('tr', {},
            h('td', {}, h('strong', { text: m.name }), h('div', { class: 'ad-sub', text: m.email })),
            h('td', {}, h('span', { class: `ad-tag ${m.active ? 'is-ok' : 'is-warn'}`, text: m.active ? 'Active' : 'Deactivated' })),
            h('td', { class: 'ad-sub', text: ago(m.createdAt) }),
            h('td', { class: 'ad-right' },
                h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: 'Reset password', onclick: () => reset(m) }),
                h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: m.active ? 'Deactivate' : 'Activate', onclick: async () => {
                    if (m.active && !(await confirmDialog({ title: `Deactivate ${m.name}?`, text: 'They are signed out at their next request and cannot sign in until you activate them again.', confirm: 'Deactivate', danger: true }))) return;
                    act(() => api.setModeratorActive(m.id, !m.active), m.active ? 'Deactivated.' : 'Activated.');
                } })));
    }

    function draw() {
        body.replaceChildren(...(state.all.length ? state.all.map(row) : [h('tr', {}, h('td', { colspan: 4, class: 'ad-empty', text: 'No moderators yet.' }))]));
    }

    async function create() {
        const name = field('name', 'Name'), email = field('email', 'Email', { type: 'email' }), pw = passwordField('Password (10+ characters)');
        const out = await formDialog({ title: 'New moderator', lead: 'Moderators are separate from users. You hand them the password; nothing is emailed.', fields: [name, email, pw], submit: 'Create',
            valid: () => name.input.value.trim() && email.input.value.includes('@') && pw.input.value.length >= 10 });
        if (!out) return;
        await act(() => api.createModerator({ name: out.name.trim(), email: out.email.trim(), password: out.password }), 'Moderator created.');
        if (await confirmDialog({ title: 'Copy the password now', text: 'It is not shown again.', confirm: 'Copy it' })) copy(out.password);
    }

    draw();
    return h('div', { class: 'ad-view' },
        h('div', { class: 'ad-head' }, h('h2', { text: 'Moderators' }), h('button', { class: 'ad-btn ad-btn--primary', type: 'button', text: 'New moderator', onclick: create })),
        h('p', { class: 'ad-lead', text: 'Not users: they have their own sign-in, no profile, and only see the workspaces you assign them to investigate.' }),
        h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table' },
            h('thead', {}, h('tr', {}, ...['Moderator', 'Status', 'Added', ''].map((t) => h('th', { text: t })))), body)));
}
