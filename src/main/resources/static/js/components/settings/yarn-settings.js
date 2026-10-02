// The Yarns part of Settings, each a sub-modal: the chat that opens first, the Archive and the Blocked list.
// Text goes in through textContent only (h() never sets innerHTML for user text).
import { settingsDialog } from '/js/components/settings/dialog.js';
import { h, toast } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { yarnThreads, yarnPrefs, yarnBlocked, yarnUnblock } from '/js/services/api.js';
import { SECTIONS, TIER_LABEL, ago } from '/js/pages/yarnspaces-data.js';

const PREF_KEY = 'collaboYarnDefault';   // which section opens when Yarns is entered (read by yarnspaces.js)
let archived = [], blocked = [];

export const loadYarnLists = async () => { [archived, blocked] = await Promise.all([yarnThreads('archived'), yarnBlocked()]); };
export const archivedCount = () => `${archived.length} archived`;
export const blockedCount = () => `${blocked.length} blocked`;

const savedChat = () => { try { return localStorage.getItem(PREF_KEY); } catch { return null; } };   // private mode: the first section
const chat = () => SECTIONS.find((s) => s.id === savedChat()) || SECTIONS[0];
export const defaultChatName = () => chat().label;

export async function openDefaultChat(onClose) {
    const body = await settingsDialog('Default chat', onClose);
    const draw = () => body.replaceChildren(...SECTIONS.map((s) => h('button', { class: `pst-opt${s.id === chat().id ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(s.id === chat().id),
        onclick: () => { try { localStorage.setItem(PREF_KEY, s.id); toast('Default chat saved.'); } catch { toast('Could not save in this browser.'); } draw(); } }, h('strong', { text: s.label }))),
    h('p', { class: 'yn-hint', text: 'What opens first when you tap Yarns.' }));
    draw();
}

async function change(action, done, draw) {
    try { await action(); await loadYarnLists(); draw(); toast(done); } catch (err) { toast(err.message); }
}

export async function openArchive(onClose) {
    const body = await settingsDialog('Archive', onClose);
    const draw = () => body.replaceChildren(...(archived.length ? archived.map((t) => h('div', { class: 'yn-item' },
        h('a', { class: 'yn-row', href: `/HTML-pages/yarnspaces.html#t/${t.id}` },
            h('span', { class: 'yn-body' },
                h('span', { class: 'yn-line' }, h('strong', { text: t.name }), h('span', { class: 'sp-tag', text: TIER_LABEL[t.tier] }), h('time', { text: ago(t.lastAt) })),
                h('span', { class: 'yn-last', text: `${t.lastSender}: ${t.lastBody || ''}` }))),
        h('button', { class: 'yn-quick', type: 'button', text: 'Restore', onclick: () => change(() => yarnPrefs(t.id, { archived: false }), 'Restored to your inbox.', draw) })))
        : [h('p', { class: 'yn-empty', text: 'Nothing archived.' })]));
    draw();
}

export async function openBlocked(onClose) {
    const body = await settingsDialog('Blocked', onClose);
    const draw = () => body.replaceChildren(h('p', { class: 'yn-hint', text: 'They are not told. Neither of you can send new MySpace yarns. Shared WeSpaces and WorkSpaces are unaffected.' }),
        ...(blocked.length ? blocked.map((b) => h('div', { class: 'yn-item' },
            h('div', { class: 'yn-row yn-row--static' }, face(b.username, 'sp-avatar--sm'),
                h('span', { class: 'yn-body' }, h('strong', { text: b.username }), h('span', { class: 'yn-last', text: ago(b.since) === 'now' ? 'Blocked just now' : `Blocked ${ago(b.since)} ago` }))),
            h('button', { class: 'yn-quick', type: 'button', text: 'Unblock', onclick: () => change(() => yarnUnblock(b.userId), `Unblocked ${b.username}.`, draw) })))
            : [h('p', { class: 'yn-empty', text: 'You have not blocked anyone.' })]));
    draw();
}
