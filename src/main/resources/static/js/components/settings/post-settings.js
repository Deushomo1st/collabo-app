// The post's and the report's settings, each a sub-modal of Settings: who can see the post, comments, shout-outs, anonymous.
// They belong to the post or report you are writing, so everything is kept in this tab (stash.js) and the page sends it when you publish.
// Text goes in through textContent only.
import { settingsDialog } from '/js/components/settings/dialog.js';
import { postSet, reportSet, DEFAULT_POST_SET } from '/js/services/stash.js';
import { h, toast } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { followers, yarnThreads } from '/js/services/api.js';

const KINDS = [
    ['EVERYONE', 'Everyone', 'Anyone on The Gaze.'],
    ['FOLLOWERS', 'Followers', 'Private: only people who follow you.'],
    ['COMMUNITY', 'Community', 'People in one of your WeSpaces or WorkSpaces.'],
    ['PEOPLE', 'People', 'People you pick from your followers.'],
];
const SWITCHES = [
    ['applicationsOn', 'Applications', 'People can apply to join the idea. Turn it off for a regular post: no Apply button, no deadline.'],
    ['commentsOn', 'Comments', 'People can comment on it.'],
    ['shoutsOn', 'Shout-outs', 'People can shout it out to their network.'],
    ['anonymous', 'Stay anonymous', 'Others see "Anonymous" instead of your name. It stays off your profile. A space formed from it still lists you as its owner.'],
];
const read = () => { const st = postSet.read() || {}; return { ...DEFAULT_POST_SET, ...st, ui: { ...DEFAULT_POST_SET.ui, ...(st.ui || {}) } }; };

/** The lines under the rows in Settings. */
export const audienceName = () => KINDS.find(([k]) => k === read().ui.kind)?.[1] ?? 'Everyone';
export const postOptionsNow = () => { const s = read(); return SWITCHES.filter(([k]) => s[k]).map(([k, t]) => (k === 'anonymous' ? 'Anonymous' : t)).join(', ') || 'All off'; };
export const reportAnonymousNow = () => (reportSet.read()?.anonymous ? 'Anonymous' : 'Shows your name');

const switchRow = (label, hint, checked, onchange) => {
    const box = h('input', { type: 'checkbox', checked, onchange: () => onchange(box.checked) });
    return h('label', { class: 'pst-switch' }, box, h('span', {}, h('strong', { text: label }), h('small', { text: hint })));
};

export async function openPostOptions(onClose) {
    const body = await settingsDialog('On the post', onClose);
    let s = read();
    body.replaceChildren(...SWITCHES.map(([k, t, d]) => switchRow(t, d, s[k], (on) => { s = { ...s, [k]: on }; postSet.write(s); })));
}

export async function openReportOptions(onClose) {
    const body = await settingsDialog('On the report', onClose);
    const s = { anonymous: false, ...(reportSet.read() || {}) };
    body.replaceChildren(switchRow('Stay anonymous', 'The admin console shows "Anonymous" instead of your name. We still keep it on our side to stop abuse.', s.anonymous, (on) => { s.anonymous = on; reportSet.write(s); }));
}

export async function openPostAudience(me, onClose) {
    const body = await settingsDialog('Who can see it', onClose);
    let s = read(), rooms = [], fans = [], find = '';
    const room = () => rooms.find((r) => String(r.id) === String(s.ui.room));
    const pool = () => (s.ui.kind === 'COMMUNITY' ? (room()?.members || []).map((m) => m.username).filter((n) => n !== me) : fans);
    /** What the server is sent. A community "exclude" is the whole room minus the ticked people. */
    const resolve = () => {
        const { kind, mode, ticked } = s.ui;
        if (kind === 'FOLLOWERS') return ['FOLLOWERS', []];
        if (kind === 'COMMUNITY') return ['ONLY', mode === 'in' ? ticked.filter((n) => pool().includes(n)) : pool().filter((n) => !ticked.includes(n))];
        if (kind === 'PEOPLE') return [mode === 'in' ? 'ONLY' : 'EXCEPT', ticked];
        return ['EVERYONE', []];
    };
    const set = (patch) => { s = { ...s, ...patch }; [s.audience, s.audienceWith] = resolve(); postSet.write(s); draw(); };
    const setUi = (patch) => set({ ui: { ...s.ui, ...patch } });

    const pickList = () => {
        const q = find.trim().toLowerCase();
        const names = pool().filter((n) => !q || n.toLowerCase().includes(q));
        if (!pool().length) return h('p', { class: 'pst-empty', text: s.ui.kind === 'COMMUNITY' ? 'Pick a community first.' : 'No followers yet.' });
        return h('div', { class: 'pst-list' }, ...(names.length ? names.map((n) => {
            const on = s.ui.ticked.includes(n);
            return h('button', { class: `pst-person${on ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(on),
                onclick: () => setUi({ ticked: on ? s.ui.ticked.filter((x) => x !== n) : [...s.ui.ticked, n] }) },
            face(n, 'sp-avatar--sm'), h('span', { class: 'pst-person__n', text: n }), h('span', { class: 'pst-tick', text: on ? '✓' : '' }));
        }) : [h('p', { class: 'pst-empty', text: 'No one by that name.' })]));
    };

    const chooser = () => {
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
    };

    function draw() {
        body.replaceChildren(...KINDS.map(([k, t, d]) => h('button', { class: `pst-opt${s.ui.kind === k ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(s.ui.kind === k),
            onclick: () => setUi({ kind: k, ticked: [], room: k === 'COMMUNITY' ? s.ui.room : null }) }, h('strong', { text: t }), h('small', { text: d }))), chooser());
    }
    draw();
    try {
        const [we, work, fol] = await Promise.all([yarnThreads('inbox', 'WESPACE'), yarnThreads('inbox', 'WORKSPACE'), followers(me)]);
        rooms = [...we, ...work].filter((t) => t.status !== 'DECLINED');
        fans = (Array.isArray(fol) ? fol : []).map((f) => f.username);
    } catch (err) { toast(err.message); }
    set({});
}
