// Users: search, filter, change role, toggle premium, create (one or in bulk), delete. The last ADMIN can be neither demoted nor deleted (self-lockout guard).
import { h } from '/js/services/dom.js';
import * as api from '/js/services/admin-api.js';
import { notify, field, confirmDialog, formDialog, ago, copy } from './ui.js';

const FILTERS = [['all', 'All', () => true], ['unverified', 'Unverified', (u) => !u.verified], ['premium', 'Premium', (u) => u.premium],
    ['admin', 'Admins', (u) => u.role === 'ADMIN'], ['test', 'Test', (u) => u.test]];

export async function usersView() {
    const state = { all: await api.users(), q: '', filter: 'all', sel: new Set() };
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
            h('td', {}, h('input', { type: 'checkbox', 'aria-label': `Select ${u.username}`, checked: state.sel.has(u.id), onchange: (e) => { e.target.checked ? state.sel.add(u.id) : state.sel.delete(u.id); drawSel(); } })),
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
        state.sel = new Set([...state.sel].filter((id) => state.all.some((u) => u.id === id)));
        state.shown = shown;
        body.replaceChildren(...(shown.length ? shown.map(row) : [h('tr', {}, h('td', { colspan: 7, class: 'ad-empty', text: 'No users match.' }))]));
        drawSel();
    }

    const delSel = h('button', { class: 'ad-btn ad-btn--danger-quiet', type: 'button', hidden: true });
    const allBox = h('input', { type: 'checkbox', 'aria-label': 'Select all shown', onchange: (e) => { (state.shown || []).forEach((u) => (e.target.checked ? state.sel.add(u.id) : state.sel.delete(u.id))); draw(); } });
    function drawSel() {
        const n = state.sel.size;
        delSel.hidden = !n; delSel.textContent = `Delete selected (${n})`;
        allBox.checked = !!state.shown?.length && state.shown.every((u) => state.sel.has(u.id));
    }
    /** Mass delete: one request per account, so a failure stops nothing else. The last ADMIN is always kept. */
    async function deleteSelected() {
        const picked = state.all.filter((u) => state.sel.has(u.id));
        let keep = admins();
        const doomed = picked.filter((u) => { if (u.role !== 'ADMIN') return true; if (keep <= 1) return false; keep--; return true; });
        const spared = picked.length - doomed.length;
        if (!doomed.length) return notify('Nothing to delete: the last ADMIN is kept.', true);
        if (!(await confirmDialog({ title: `Delete ${doomed.length} user${doomed.length === 1 ? '' : 's'}?`, text: `Their posts, comments, memberships and credentials go with them. This cannot be undone.${spared ? ' The last ADMIN is kept.' : ''}`, confirm: 'Delete', danger: true }))) return;
        let failed = 0;
        for (const u of doomed) { try { await api.deleteUser(u.id); } catch (e) { if (e instanceof api.AdminAuthError) throw e; failed++; } }
        state.sel.clear(); state.all = await api.users(); draw();
        notify(failed ? `${doomed.length - failed} deleted, ${failed} failed.` : `${doomed.length} deleted.`, !!failed);
    }
    delSel.addEventListener('click', deleteSelected);

    async function create() {
        const f = { email: field('email', 'Email', { type: 'email' }), username: field('username', 'Username'), password: field('password', 'Password (8+ characters)', { type: 'password', autocomplete: 'new-password' }),
            role: field('role', 'Role', { tag: 'select' }) };
        ['USER', 'ADMIN'].forEach((r) => f.role.input.append(h('option', { value: r, text: r })));
        const test = field('test', 'Test account (skips email checks)', { type: 'checkbox' });
        const out = await formDialog({ title: 'New user', fields: [...Object.values(f), test], submit: 'Create',
            valid: () => f.email.input.value.includes('@') && f.username.input.value.trim() && f.password.input.value.length >= 8 });
        if (out) act(() => api.createUser({ email: out.email.trim(), username: out.username.trim(), password: out.password, role: out.role, test: out.test }), 'User created.');
    }

    /** One line per account: email, username, and optionally a password (comma or tab separated). A blank password is generated. */
    const parseRows = (text) => text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean).map((l) => l.split(/[,\t]/).map((c) => c.trim()))
        .filter(([email]) => email.toLowerCase() !== 'email').map(([email, username, password]) => ({ email, username, password: password || '' }));

    const csvCell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    function download(results) {
        const csv = ['email,username,password,result', ...results.map((r) => [r.email, r.username, r.password, r.ok ? 'created' : r.error].map(csvCell).join(','))].join('\n');
        const a = h('a', { href: URL.createObjectURL(new Blob([csv], { type: 'text/csv' })), download: 'new-accounts.csv' });
        a.click(); URL.revokeObjectURL(a.href);
    }

    /** The outcome of a bulk run. Generated passwords are shown here once and are not stored anywhere readable. */
    function showResults(results) {
        const dlg = h('dialog', { class: 'ad-dialog ad-dialog--wide' });
        const ok = results.filter((r) => r.ok).length;
        dlg.append(h('h3', { text: `${ok} of ${results.length} accounts created` }),
            h('p', { class: 'ad-dialog__lead', text: 'Generated passwords appear only here. Download or copy them now.' }),
            h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table' },
                h('thead', {}, h('tr', {}, ...['#', 'Email', 'Username', 'Password / problem'].map((t) => h('th', { text: t })))),
                h('tbody', {}, ...results.map((r) => h('tr', {}, h('td', { text: String(r.row) }), h('td', { text: r.email }), h('td', { text: r.username }),
                    h('td', { class: r.ok ? '' : 'ad-sub', text: r.ok ? (r.password || 'as supplied') : r.error })))))),
            h('div', { class: 'ad-dialog__actions' },
                h('button', { class: 'ad-btn', type: 'button', text: 'Copy', onclick: () => copy(results.map((r) => [r.email, r.username, r.password || (r.ok ? '' : r.error)].join('\t')).join('\n')) }),
                h('button', { class: 'ad-btn', type: 'button', text: 'Download CSV', onclick: () => download(results) }),
                h('button', { class: 'ad-btn ad-btn--primary', type: 'button', text: 'Done', onclick: () => dlg.close() })));
        dlg.addEventListener('close', () => dlg.remove());
        document.body.append(dlg);
        dlg.showModal();
    }

    async function bulk() {
        const rows = field('rows', 'email, username, password (optional): one account per line', { tag: 'textarea', rows: 8, placeholder: 'ann@example.com, ann\nbob@example.com, bob, Chosen#Pass1' });
        const test = field('test', 'Test accounts (skip email checks)', { type: 'checkbox' });
        const out = await formDialog({ title: 'Create many users', lead: 'Paste a list, or a CSV from a spreadsheet. Rows without a password get a generated one.', fields: [rows, test], submit: 'Create',
            valid: () => parseRows(rows.input.value).length > 0 });
        if (!out) return;
        const list = parseRows(out.rows);
        if (list.length > 500) return notify('At most 500 accounts at a time.', true);
        try { showResults(await api.createUsers(list, out.test)); state.all = await api.users(); draw(); }
        catch (e) { if (e instanceof api.AdminAuthError) throw e; notify(e.message, true); }
    }

    draw();
    return h('div', { class: 'ad-view' },
        h('div', { class: 'ad-head' }, h('h2', { text: 'Users' }), h('span', {}, h('button', { class: 'ad-btn', type: 'button', text: 'Bulk create', onclick: bulk }), ' ', h('button', { class: 'ad-btn ad-btn--primary', type: 'button', text: 'New user', onclick: create }))),
        h('div', { class: 'ad-toolbar' }, search, chips, count, delSel),
        h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table' },
            h('thead', {}, h('tr', {}, h('th', {}, allBox), ...['User', 'Role', 'Status', 'Premium', 'Joined', ''].map((t) => h('th', { text: t })))), body)));
}
