// The black "Tasks" pill that hangs under a Workspace chat's header; tap it and the list drops down.
// mountIsland(el, postId, me) -> { reload, destroy, canPin, pin(yarn) }. The dropdown also lists the room's pinned yarns. Styles are the .sp-island rules in workspace.css. Text goes in through textContent only.
import { tasksOf, taskCreate, taskUpdate, spaceOfPost, spaceMembers, pinsOf, pinSet } from '/js/services/api.js';
import { h, toast } from '/js/services/dom.js';

const hue = (name) => [...name].reduce((n, c) => n + c.charCodeAt(0), 0) * 37 % 360;
const dot = (name) => h('span', { class: 'sp-avatar sp-avatar--sm', style: `--hue:${hue(name)}`, title: name, text: name[0].toUpperCase() });

export async function mountIsland(el, postId, me) {
    let space, members = [], rows = [], pins = [], open = false, gone = false;
    try { space = await spaceOfPost(postId); members = await spaceMembers(space.id); } catch { return { reload() {}, destroy() {}, canPin: false, pin() {} }; }
    if (gone) return { reload() {}, destroy() {}, canPin: false, pin() {} };

    const canPin = space.owner.username === me.username || !!members.find((m) => m.person.username === me.username)?.permissions.includes('POST_IN_ROOM');
    const pin = (y) => run(() => pinSet(space.id, y.id, !y.pinned));
    const run = async (fn) => { try { await fn(); await reload(); } catch (err) { toast(err.message); } };

    function draw() {
        const todo = rows.filter((t) => t.status !== 'DONE');
        const latest = todo[todo.length - 1];
        const list = h('ul', { class: 'sp-island__list' }, ...rows.map((t) => {
            const box = h('input', { type: 'checkbox', 'aria-label': `Mark "${t.title}" done`, onchange: () => run(() => taskUpdate(space.id, t.id, { status: box.checked ? 'DONE' : 'TODO' })) });
            box.checked = t.status === 'DONE';
            return h('li', { class: `sp-task ${box.checked ? 'is-done' : ''}` },
                h('label', { class: 'sp-check' }, box, h('span')),
                h('span', { class: 'sp-task__title', text: t.title }),
                t.assignee ? dot(t.assignee.username)
                    : h('button', { class: 'sp-btn', type: 'button', text: 'Take it', onclick: () => run(() => taskUpdate(space.id, t.id, { assignee: me.username })) }));
        }));
        const pinned = pins.length && h('div', { class: 'sp-island__pins' }, h('small', { text: 'Pinned' }),
            ...pins.map((p) => h('p', {}, h('strong', { text: p.sender + ': ' }), p.body, canPin && h('button', { class: 'sp-btn', type: 'button', text: 'Unpin', onclick: () => pin(p) }))));
        const title = h('input', { class: 'sp-input', maxlength: 140, placeholder: 'Add a task', 'aria-label': 'New task' });
        const add = h('form', { class: 'sp-island__add' }, title, h('button', { class: 'sp-btn sp-btn--brand', type: 'submit', text: 'Add' }));
        add.addEventListener('submit', (e) => { e.preventDefault(); if (title.value.trim()) run(() => taskCreate(space.id, title.value, '')); });

        const pill = h('button', { class: 'sp-island__pill', type: 'button', 'aria-expanded': String(open), onclick: () => setOpen(!open) },
            h('span', { class: 'sp-island__dot' }),
            h('span', { class: 'sp-island__text' }, 'Tasks', h('small', { text: `${todo.length} open` })),
            h('span', { class: 'sp-island__latest', text: latest ? latest.title : rows.length ? 'All done' : 'None yet' }),
            h('span', { class: 'sp-island__chev', text: '⌄' }));
        el.replaceChildren(pill, h('div', { class: 'sp-island__body' }, h('div', { class: 'sp-island__inner' }, pinned || null, list, add)));
    }

    function setOpen(v) {
        open = v;
        el.classList.toggle('is-open', open);
        el.querySelector('.sp-island__pill')?.setAttribute('aria-expanded', String(open));
    }
    // Tap anywhere outside the island and it folds back to the pill.
    const away = (e) => { if (open && !el.contains(e.target)) setOpen(false); };
    document.addEventListener('pointerdown', away);

    async function reload() { try { [rows, pins] = await Promise.all([tasksOf(space.id), pinsOf(space.id)]); if (!gone) draw(); } catch { /* the chat still works without the island */ } }

    el.hidden = false;
    el.className = 'sp-island';
    await reload();
    return { reload, canPin, pin, destroy() { gone = true; document.removeEventListener('pointerdown', away); el.hidden = true; el.replaceChildren(); el.classList.remove('is-open'); } };
}
