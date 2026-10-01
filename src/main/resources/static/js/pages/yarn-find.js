// Yarn search, WhatsApp style: a bubble that grows out of the search icon. People show as round pictures along the top
// (the name on hover); chats whose name or words match show as tablets underneath. Text goes in through textContent only.
import { h } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { yarnDirectory, yarnFind } from '/js/services/api.js';
import { ago } from '/js/pages/yarnspaces-data.js';

const SNIPPET = 70;

/** The words around the first match, the match itself marked. */
function snippet(body, text) {
    const at = body.toLowerCase().indexOf(text.toLowerCase());
    if (at < 0) return [body.slice(0, SNIPPET)];
    const from = Math.max(0, at - 24), end = at + text.length;
    return [(from ? '…' : '') + body.slice(from, at), h('mark', { text: body.slice(at, end) }), body.slice(end, end + SNIPPET)];
}

export function openFind({ me, threads, openThread, startWith }) {
    const input = h('input', { class: 'yn-find__input', type: 'search', placeholder: 'Search people or words in your yarns', 'aria-label': 'Search yarns', autocomplete: 'off' });
    const people = h('div', { class: 'gz-circles yn-find__people', 'aria-label': 'People' });
    const tablets = h('div', { class: 'yn-find__list' });
    const bubble = h('div', { class: 'yn-find__bubble sp-glass', role: 'dialog', 'aria-label': 'Search yarns' },
        h('div', { class: 'yn-find__bar' }, input, h('button', { class: 'yn-find__x', type: 'button', 'aria-label': 'Close search', text: '×', onclick: close })),
        people, tablets);
    const shade = h('div', { class: 'yn-find' }, bubble);
    shade.addEventListener('pointerdown', (e) => { if (e.target === shade) close(); });
    document.body.append(shade);
    requestAnimationFrame(() => shade.classList.add('is-open'));   // the next frame, so the grow transition runs
    input.focus();
    document.addEventListener('keydown', onKey);
    function onKey(e) { if (e.key === 'Escape') close(); }
    function close() { document.removeEventListener('keydown', onKey); shade.classList.remove('is-open'); setTimeout(() => shade.remove(), 220); }

    const all = threads(), byId = new Map(all.map((t) => [t.id, t]));
    const mySpaceWith = (id) => all.find((t) => t.tier === 'MYSPACE' && t.otherUserId === id);
    const pick = (person) => { close(); const t = mySpaceWith(person.id); if (t) openThread(t.id); else startWith(person.username); };

    const circle = (p) => h('button', { class: 'gz-circle', type: 'button', 'data-name': p.username, 'aria-label': p.username, onclick: () => pick(p) }, face(p.username));
    const tablet = (t, who, body, at, text) => h('button', { class: 'yn-find__tablet', type: 'button', onclick: () => { close(); openThread(t.id); } },
        h('span', { class: 'yn-find__top' }, h('strong', { text: t.name }), h('time', { text: ago(at) })),
        h('span', { class: 'yn-find__text' }, who ? `${who}: ` : '', ...snippet(body, text)));

    let gen = 0, timer;
    async function run() {
        const text = input.value.trim(), mine = ++gen, low = text.toLowerCase();
        const known = new Map();   // people already in my yarns, newest chat first
        for (const t of all) for (const m of t.members) if (m.id !== me.id && !known.has(m.id)) known.set(m.id, m);
        let found = [...known.values()].filter((m) => !low || m.username.toLowerCase().includes(low));
        let hits = [];
        if (text.length >= 2) {
            const [dir, words] = await Promise.all([yarnDirectory(text).catch(() => []), yarnFind(text).catch(() => [])]);
            if (mine !== gen) return;
            found = [...found, ...dir.filter((p) => !known.has(p.id))];
            hits = words;
        }
        people.replaceChildren(...found.slice(0, 20).map(circle));
        people.hidden = !found.length;
        const rooms = text ? all.filter((t) => t.tier !== 'MYSPACE' && t.name.toLowerCase().includes(low)) : [];
        const rows = [...rooms.map((t) => tablet(t, t.lastSender, t.lastBody || '', t.lastAt, text)),
            ...hits.filter((y) => byId.has(y.threadId)).map((y) => {
                const t = byId.get(y.threadId), s = t.members.find((m) => m.id === y.senderId);
                return tablet(t, s ? (s.id === me.id ? 'You' : s.username) : '', y.body, y.at, text);
            })];
        tablets.replaceChildren(...(rows.length ? rows : [h('p', { class: 'yn-empty', text: !text ? 'Search names, or words from your chats.' : text.length < 2 ? 'Type at least two letters to search words.' : 'No chats match.' })]));
    }
    input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(run, 250); });
    run();
}
