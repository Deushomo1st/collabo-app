// Database: every table with its row count, a read-only row browser (secrets masked by the server) and a typed-confirm "Clean" for
// the few tables that may be wiped. This replaces the routine Adminer jobs; Adminer stays for anything that edits.
import { h } from '/js/services/dom.js';
import * as api from '/js/services/admin-api.js';
import { notify, typedConfirm } from './ui.js';

const PAGE = 50;

async function browser(name, back) {
    let offset = 0;
    const holder = h('div');
    async function load() {
        let page;
        try { page = await api.tableRows(name, PAGE, offset); }
        catch (e) { if (e instanceof api.AdminAuthError) throw e; return holder.replaceChildren(h('p', { class: 'ad-empty', text: e.message })); }
        const from = page.total === 0 ? 0 : offset + 1, to = offset + page.rows.length;
        holder.replaceChildren(
            h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table ad-table--data' },
                h('thead', {}, h('tr', {}, ...page.columns.map((c) => h('th', { text: c })))),
                h('tbody', {}, ...(page.rows.length ? page.rows.map((r) => h('tr', {}, ...r.map((v) => v === null
                    ? h('td', { class: 'ad-null', text: 'null' })
                    : h('td', { class: v === '••••' ? 'ad-masked' : '', title: v, text: v }))))
                    : [h('tr', {}, h('td', { colspan: page.columns.length, class: 'ad-empty', text: 'No rows.' }))])))),
            h('div', { class: 'ad-pager' },
                h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: '← Previous', disabled: offset === 0, onclick: () => { offset -= PAGE; load(); } }),
                h('span', { class: 'ad-sub', text: `${from}–${to} of ${page.total}` }),
                h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: 'Next →', disabled: to >= page.total, onclick: () => { offset += PAGE; load(); } })));
    }
    await load();
    return h('div', { class: 'ad-view' },
        h('div', { class: 'ad-head' }, h('h2', {}, h('button', { class: 'ad-link', type: 'button', text: 'Database', onclick: back }), ` / ${name}`)),
        h('p', { class: 'ad-lead', text: 'Read-only. Password, hash, token and similar columns are masked. Hover a cell to see it in full.' }), holder);
}

export async function databaseView() {
    const root = h('div');
    const show = (node) => root.replaceChildren(node);

    async function list() {
        const tables = await api.tables();
        const body = h('tbody', {}, ...tables.map((t) => h('tr', {},
            h('td', {}, h('code', { text: t.name })),
            h('td', { class: 'ad-num', text: String(t.rowCount) }),
            h('td', { class: 'ad-right' },
                h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: 'Browse', onclick: async () => show(await browser(t.name, async () => show(await list()))) }),
                t.canClean && h('button', { class: 'ad-btn ad-btn--small ad-btn--danger-quiet', type: 'button', text: 'Clean', onclick: async () => {
                    if (!(await typedConfirm({ title: `Clean ${t.name}`, text: `This deletes every row in ${t.name}, and everything that depends on them. Use it for testing only.`, expected: t.name, confirm: 'Delete all rows' }))) return;
                    try { await api.cleanTable(t.name); notify(`${t.name} cleaned.`); show(await list()); }
                    catch (e) { if (e instanceof api.AdminAuthError) throw e; notify(e.message, true); }
                } })))));
        return h('div', { class: 'ad-view' }, h('h2', { text: 'Database' }),
            h('p', { class: 'ad-lead', text: 'Browse any table, read-only. Only a few tables can be cleaned from here. For edits, use Adminer (see Help).' }),
            h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table' }, h('thead', {}, h('tr', {}, ...['Table', 'Rows', ''].map((t) => h('th', { text: t })))), body)));
    }

    show(await list());
    return root;
}
