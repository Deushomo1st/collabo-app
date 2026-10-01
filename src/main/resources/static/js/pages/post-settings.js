// Post settings, a page of its own: who can see the post, comments, shout-outs, anonymous.
// Everything is kept in this tab (stash.js) and the post page sends it when you publish. Text goes in through textContent only.
import { mountSettingsNav } from '/js/services/settings-nav.js';
import { postSet, DEFAULT_POST_SET } from '/js/services/stash.js';
import { h, toast } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { currentUser, followers, yarnThreads } from '/js/services/api.js';

const KINDS = [
    ['EVERYONE', 'Everyone', 'Anyone on The Gaze.'],
    ['FOLLOWERS', 'Followers', 'Private: only people who follow you.'],
    ['COMMUNITY', 'Community', 'People in one of your WeSpaces or WorkSpaces.'],
    ['PEOPLE', 'People', 'People you pick from your followers.'],
];
const root = () => document.getElementById('section');
let me, rooms = [], fans = [], find = '';

const stored = postSet.read() || {};
let s = { ...DEFAULT_POST_SET, ...stored, ui: { ...DEFAULT_POST_SET.ui, ...(stored.ui || {}) } };

const room = () => rooms.find((r) => String(r.id) === String(s.ui.room));
const pool = () => (s.ui.kind === 'COMMUNITY' ? (room()?.members || []).map((m) => m.username).filter((n) => n !== me) : fans);

/** What the server is sent. A community "exclude" is the whole room minus the ticked people. */
function resolve() {
    const { kind, mode, ticked } = s.ui;
    if (kind === 'FOLLOWERS') return ['FOLLOWERS', []];
    if (kind === 'COMMUNITY') return ['ONLY', mode === 'in' ? ticked.filter((n) => pool().includes(n)) : pool().filter((n) => !ticked.includes(n))];
    if (kind === 'PEOPLE') return [mode === 'in' ? 'ONLY' : 'EXCEPT', ticked];
    return ['EVERYONE', []];
}

function save() {
    [s.audience, s.audienceWith] = resolve();
    postSet.write(s);
}

const set = (patch) => { s = { ...s, ...patch }; save(); draw(); };
const setUi = (patch) => set({ ui: { ...s.ui, ...patch } });

function switchRow(label, hint, key) {
    const box = h('input', { type: 'checkbox', checked: s[key], onchange: () => set({ [key]: box.checked }) });
    return h('label', { class: 'pst-switch' }, box, h('span', {}, h('strong', { text: label }), h('small', { text: hint })));
}

function pickList() {
    const q = find.trim().toLowerCase();
    const names = pool().filter((n) => !q || n.toLowerCase().includes(q));
    if (!pool().length) return h('p', { class: 'pst-empty', text: s.ui.kind === 'COMMUNITY' ? 'Pick a community first.' : 'No followers yet.' });
    return h('div', { class: 'pst-list' }, ...(names.length ? names.map((n) => {
        const on = s.ui.ticked.includes(n);
        return h('button', { class: `pst-person${on ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(on),
            onclick: () => setUi({ ticked: on ? s.ui.ticked.filter((x) => x !== n) : [...s.ui.ticked, n] }) },
        face(n, 'sp-avatar--sm'), h('span', { class: 'pst-person__n', text: n }), h('span', { class: 'pst-tick', text: on ? '✓' : '' }));
    }) : [h('p', { class: 'pst-empty', text: 'No one by that name.' })]));
}

function chooser() {
    const { kind, mode } = s.ui;
    if (kind !== 'COMMUNITY' && kind !== 'PEOPLE') return null;
    const pickRoom = kind === 'COMMUNITY' && h('select', { class: 'sp-input', 'aria-label': 'Community', onchange: (e) => setUi({ room: e.target.value || null, ticked: [] }) },
        h('option', { value: '', text: rooms.length ? 'Choose a community' : 'You are in no WeSpace or WorkSpace yet' }),
        ...rooms.map((r) => h('option', { value: String(r.id), text: r.name, selected: String(r.id) === String(s.ui.room) })));
    const seg = h('div', { class: 'pst-seg', role: 'group', 'aria-label': 'Include or exclude' },
        ...[['in', 'Include'], ['out', 'Exclude']].map(([v, t]) => h('button', { class: v === mode ? 'is-on' : '', type: 'button', text: t, onclick: () => setUi({ mode: v }) })));
    const search = h('input', { class: 'sp-input', placeholder: 'Search people', 'aria-label': 'Search people', autocomplete: 'off', value: find });
    search.addEventListener('input', () => { find = search.value; document.getElementById('pst-pick').replaceChildren(pickList()); });
    const ids = resolve()[1];
    const note = mode === 'in' && !ids.length ? 'Pick at least one person, or nobody but you will see it.'
        : kind === 'COMMUNITY' && mode === 'out' ? 'The whole community sees it except the people you tick.'
        : mode === 'out' ? 'Everyone sees it except the people you tick.' : `${ids.length} can see it besides you.`;
    return h('div', { class: 'pst-choose' }, pickRoom, seg, search, h('div', { id: 'pst-pick' }, pickList()), h('p', { class: 'pst-note', text: note }));
}

function draw() {
    root().replaceChildren(
        h('section', { class: 'pst-card sp-glass' }, h('h2', { text: 'Who can see it' }),
            ...KINDS.map(([k, t, d]) => h('button', { class: `pst-opt${s.ui.kind === k ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(s.ui.kind === k),
                onclick: () => setUi({ kind: k, ticked: [], room: k === 'COMMUNITY' ? s.ui.room : null }) }, h('strong', { text: t }), h('small', { text: d }))),
            chooser()),
        h('section', { class: 'pst-card sp-glass' }, h('h2', { text: 'On the post' }),
            switchRow('Comments', 'People can comment on it.', 'commentsOn'),
            switchRow('Shout-outs', 'People can shout it out to their network.', 'shoutsOn'),
            switchRow('Stay anonymous', 'Others see "Anonymous" instead of your name. It stays off your profile. A space formed from it still lists you as its owner.', 'anonymous')));
}

async function boot() {
    mountSettingsNav('/HTML-pages/post.html');
    const user = await currentUser().catch(() => null);
    if (!user) return location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
    me = user.username;
    draw();
    try {
        const [we, work, fol] = await Promise.all([yarnThreads('inbox', 'WESPACE'), yarnThreads('inbox', 'WORKSPACE'), followers(me)]);
        rooms = [...we, ...work].filter((t) => t.status !== 'DECLINED');
        fans = (Array.isArray(fol) ? fol : []).map((f) => f.username);
    } catch (err) { toast(err.message); }
    save(); draw();
}
boot();
