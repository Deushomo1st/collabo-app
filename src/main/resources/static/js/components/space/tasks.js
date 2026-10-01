// A Workspace's tasks: To do / Doing / Done, who has each one, and a box to add more. Only the team sees them.
// tasksPanel(space, { me, members }) returns a self-refreshing element.
// Text goes in through textContent only.
import { tasksOf, taskCreate, taskUpdate, taskDelete } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';
import { drawTabs } from '/js/services/review-ui.js';

const TABS = [['TODO', 'To do'], ['DOING', 'Doing'], ['DONE', 'Done']];

export function tasksPanel(space, { me, members }) {
    const root = h('div', { class: 'tk' });
    const tabs = h('nav', { class: 'cn-tabs', role: 'tablist' });
    const list = h('div', { class: 'tk-list', 'aria-live': 'polite' });
    let rows = [], tab = 'TODO';

    const title = h('input', { class: 'sp-input', maxlength: 140, placeholder: 'A new task', 'aria-label': 'Task title', required: true });
    const who = h('select', { class: 'sp-input ap-select', 'aria-label': 'Give it to' }, h('option', { value: '', text: 'Anyone' }),
        ...members.map((m) => h('option', { value: m.person.username, text: m.person.username === me.username ? 'Me' : m.person.username })));
    const form = h('form', { class: 'ap-tools' }, title, who, h('button', { class: 'pc-btn pc-btn--brand', type: 'submit', text: 'Add' }));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await taskCreate(space.id, title.value, who.value); title.value = ''; tab = 'TODO'; await load(); } catch (err) { toast(err.message); }
    });

    const change = (t, changes) => async () => { try { await taskUpdate(space.id, t.id, changes); await load(); } catch (err) { toast(err.message); } };

    function row(t) {
        const mine = t.assignee?.username === me.username;
        return h('div', { class: 'spc-member tk-row' },
            h('strong', { class: 'tk-title', text: t.title }),
            t.assignee ? h('a', { class: 'sp-tag sp-tag--brand', href: profileHref(t.assignee.username), text: mine ? 'You' : t.assignee.username })
                : h('button', { class: 'pc-link', type: 'button', text: 'Take it', onclick: change(t, { assignee: me.username }) }),
            h('div', { class: 'pc-actions' },
                t.status === 'TODO' && h('button', { class: 'pc-btn', type: 'button', text: 'Start', onclick: change(t, { status: 'DOING' }) }),
                t.status !== 'DONE' && h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Done', onclick: change(t, { status: 'DONE' }) }),
                t.status === 'DONE' && h('button', { class: 'pc-btn', type: 'button', text: 'Reopen', onclick: change(t, { status: 'TODO' }) }),
                h('button', { class: 'pc-link', type: 'button', text: 'Remove', onclick: async () => { try { await taskDelete(space.id, t.id); await load(); } catch (err) { toast(err.message); } } })));
    }

    function draw() {
        const of = (k) => rows.filter((t) => t.status === k);
        drawTabs(tabs, TABS.map(([k, label]) => [k, label, of(k).length]), tab, (k) => { tab = k; draw(); });
        const shown = of(tab);
        list.replaceChildren(...(shown.length ? shown.map(row) : [h('p', { class: 'pc-hint', text: rows.length ? 'Nothing here.' : 'No tasks yet. Add the first one.' })]));
    }

    async function load() {
        try { rows = await tasksOf(space.id); draw(); } catch (err) { list.replaceChildren(h('p', { class: 'pc-error', text: err.message })); }
    }
    root.append(tabs, form, list);
    load();
    return root;
}

