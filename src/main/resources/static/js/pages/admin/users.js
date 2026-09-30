// Users: search, filter, change role, toggle premium, create, delete. The last ADMIN can be neither demoted nor deleted (self-lockout guard).
import { h } from '/js/services/dom.js';
import * as api from '/js/services/admin-api.js';
import { notify, field, confirmDialog, formDialog, ago } from './ui.js';

const FILTERS = [['all', 'All', () => true], ['unverified', 'Unverified', (u) => !u.verified], ['premium', 'Premium', (u) => u.premium],
    ['admin', 'Admins', (u) => u.role === 'ADMIN'], ['test', 'Test', (u) => u.test]];

export async function usersView() {
    const state = { all: await api.users(), q: '', filter: 'all' };
    const body = h('tbody');
    const count = h('span', { class: 'ad-count' });
    const search = h('input', { class: 'ad-input ad-search', type: 'search', placeholder: 'Search email or username', 'aria-label': 'Search users',
        oninput: (e) => { state.q = e.target.value.trim().toLowerCase(); draw(); } });
    const chips = h('div', { class: 'ad-chips' }, ...FILTERS.map(([key, label]) => h('button', { class: 'ad-chip', type: 'button', 'data-key': key, text: label,
        onclick: () => { state.filter = key; draw(); } })));
    const admins = () => state.all.filter((u) => u.role === 'ADMIN').length;

    async function act(work, done) {
        try { await work(); notify(done); state.all = await api.users(); draw(); }
        catch (e) { if (e instanceof api.AdminAuthError) throw e; notify(e.message, true); draw(); }
    }

    function roleCell(u) {
        const roles = [...new Set(['USER', 'ADMIN', u.role])];   // a legacy role still shows until it is changed
        return h('select', { class: 'ad-input', 'aria-label': `Role of ${u.username}`, onchange: async (e) => {
            const role = e.target.value;
            if (u.role === 'ADMIN' && admins() <= 1) { notify('That is the only ADMIN, so it cannot be demoted.', true); return draw(); }
            if (role === 'ADMIN' && !(await confirmDialog({ title: 'Make this person an admin?', text: `${u.email} will get admin rights.`, confirm: 'Make admin' }))) return draw();
            act(() => api.setRole(u.id, role), 'Role updated.');
        } }, ...roles.map((r) => h('option', { value: r, selected: r === u.role, text: r })));
    }

    function row(u) {
        return h('tr', {},
            h('td', {}, h('strong', { text: u.username }), h('div', { class: 'ad-sub', text: u.email })),
            h('td', {}, roleCell(u)),
            h('td', {}, h('span', { class: `ad-tag ${u.verified ? 'is-ok' : 'is-warn'}`, text: u.verified ? 'Verified' : 'Unverified' }), u.test && h('span', { class: 'ad-tag is-warn', text: 'Test' })),
            h('td', {}, h('button', { class: `ad-btn ad-btn--small${u.premium ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(u.premium), text: u.premium ? 'Premium ✓' : 'Premium',
                onclick: () => act(() => api.setPremium(u.id, !u.premium), u.premium ? 'Premium removed.' : 'Premium given.') })),
            h('td', { class: 'ad-sub', text: ago(u.createdAt) }),
            h('td', { class: 'ad-right' }, h('button', { class: 'ad-btn ad-btn--small ad-btn--danger-quiet', type: 'button', text: 'Delete', onclick: async () => {
                if (u.role === 'ADMIN' && admins() <= 1) return notify('That is the only ADMIN, so it cannot be deleted.', true);
                if (await confirmDialog({ title: `Delete ${u.username}?`, text: 'Their posts, memberships and credentials go with them. This cannot be undone.', confirm: 'Delete', danger: true }))
                    act(() => api.deleteUser(u.id), 'User deleted.');
            } })));
    }

    function draw() {
        const test = FILTERS.find(([k]) => k === state.filter)[2];
        const shown = state.all.filter((u) => test(u) && (!state.q || u.email.toLowerCase().includes(state.q) || u.username.toLowerCase().includes(state.q)));
        chips.querySelectorAll('button').forEach((b) => b.classList.toggle('is-on', b.dataset.key === state.filter));
        count.textContent = `${shown.length} of ${state.all.length}`;
        body.replaceChildren(...(shown.length ? shown.map(row) : [h('tr', {}, h('td', { colspan: 6, class: 'ad-empty', text: 'No users match.' }))]));
    }

    async function create() {
        const f = { email: field('email', 'Email', { type: 'email' }), username: field('username', 'Username'), password: field('password', 'Password (8+ characters)', { type: 'password', autocomplete: 'new-password' }),
            role: field('role', 'Role', { tag: 'select' }) };
        ['USER', 'ADMIN'].forEach((r) => f.role.input.append(h('option', { value: r, text: r })));
        const test = field('test', 'Test account (skips email checks)', { type: 'checkbox' });
        const out = await formDialog({ title: 'New user', fields: [...Object.values(f), test], submit: 'Create',
            valid: () => f.email.input.value.includes('@') && f.username.input.value.trim() && f.password.input.value.length >= 8 });
        if (out) act(() => api.createUser({ email: out.email.trim(), username: out.username.trim(), password: out.password, role: out.role, test: out.test }), 'User created.');
    }

    draw();
    return h('div', { class: 'ad-view' },
        h('div', { class: 'ad-head' }, h('h2', { text: 'Users' }), h('button', { class: 'ad-btn ad-btn--primary', type: 'button', text: 'New user', onclick: create })),
        h('div', { class: 'ad-toolbar' }, search, chips, count),
        h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table' },
            h('thead', {}, h('tr', {}, ...['User', 'Role', 'Status', 'Premium', 'Joined', ''].map((t) => h('th', { text: t })))), body)));
}
