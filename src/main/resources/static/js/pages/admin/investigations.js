// Investigations: member reports of a Yarnspace (any tier) and termination appeals. You assign a moderator, read what they send
// back (with screenshots), then close a report or rule on an appeal's badge. Moderators never close anything themselves.
import { h } from '/js/services/dom.js';
import * as api from '/js/services/admin-api.js';
import { watchQueue } from '/js/services/admin-live.js';
import { notify, field, confirmDialog, formDialog, ago } from './ui.js';

const TIER = { MYSPACE: 'MySpace', WESPACE: 'WeSpace', WORKSPACE: 'Workspace' };
const STATUS = { OPEN: ['Needs a moderator', 'is-bad'], ASSIGNED: ['With a moderator', 'is-info'], REPORTED: ['Findings in', 'is-ok'], CLOSED: ['Closed', ''] };
const cut = (s, n) => (s.length > n ? `${s.slice(0, n - 1)}…` : s);
const tag = (text, cls = '') => h('span', { class: `ad-tag ${cls}`.trim(), text });
const kind = (i) => (i.kind === 'APPEAL' ? 'Appeal' : TIER[i.tier] || 'Report');

export async function investigationsView() {
    const root = h('div', { class: 'ad-view' });
    let filter = 'active';

    let showing = 'list', seen = '';

    async function list(quiet = false) {
        const rows = await api.investigations(filter);
        const now = filter + JSON.stringify(rows);
        if (quiet && now === seen) return;   // nothing new: leave the table (and your scroll) alone
        seen = now; showing = 'list';
        root.replaceChildren(
            h('div', { class: 'ad-head' }, h('h2', { text: 'Investigations' })),
            h('p', { class: 'ad-lead', text: 'Reports from members and termination appeals. Assign a moderator, read their findings, then close it or rule on the badge.' }),
            h('div', { class: 'ad-toolbar' }, h('div', { class: 'ad-chips' }, ...[['active', 'Active'], ['closed', 'Closed']].map(([key, label]) =>
                h('button', { class: `ad-chip${filter === key ? ' is-on' : ''}`, type: 'button', text: label, onclick: () => { filter = key; list(); } }))),
                h('span', { class: 'ad-count', text: `${rows.length} ${filter}` })),
            h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table' },
                h('thead', {}, h('tr', {}, ...['', 'Yarnspace', 'Reason', 'Reported by', 'Moderator', 'Opened'].map((t) => h('th', { text: t })))),
                h('tbody', {}, ...(rows.length ? rows.map(row) : [h('tr', {}, h('td', { colspan: 6, class: 'ad-empty', text: filter === 'active' ? 'Nothing needs you.' : 'Nothing closed yet.' }))])))));
    }

    const row = (i) => h('tr', { class: 'ad-row-link', tabindex: '0', onclick: () => detail(i.id), onkeydown: (e) => { if (e.key === 'Enter') detail(i.id); } },
        h('td', {}, tag(kind(i), i.kind === 'APPEAL' ? 'is-warn' : ''), tag(STATUS[i.status][0], STATUS[i.status][1])),
        h('td', { text: i.title || '(gone)' }),
        h('td', { class: 'ad-sub', text: cut(i.reason, 70) }),
        h('td', { text: i.reporter }),
        h('td', { text: i.moderator || '—' }),
        h('td', { class: 'ad-sub', text: ago(i.createdAt) }));

    async function detail(id) {
        let d;
        try { d = await api.investigation(id); } catch (e) { if (e instanceof api.AdminAuthError) throw e; notify(e.message, true); return list(); }
        showing = 'detail';
        const i = d.investigation, appeal = d.appeal;
        const again = async (work, done) => {
            try { await work(); notify(done); await detail(id); }
            catch (e) { if (e instanceof api.AdminAuthError) throw e; notify(e.message, true); }
        };

        async function assign() {
            const mods = (await api.moderators()).filter((m) => m.active);
            if (!mods.length) return notify('No active moderators. Add one under Moderators first.', true);
            const pick = field('moderatorId', 'Moderator', { tag: 'select' });
            pick.input.append(...mods.map((m) => h('option', { value: m.id, text: m.name, selected: m.id === i.moderatorId })));
            const out = await formDialog({ title: i.moderator ? 'Change moderator' : 'Assign a moderator', lead: i.kind === 'APPEAL'
                ? 'They will read the room around the removal, read-only. The person appealing is told.'
                : 'They will read this Yarnspace, read-only, while the case is open. Everyone in it is told a moderator was introduced.', fields: [pick], submit: 'Assign' });
            if (out) again(() => api.assignInvestigation(id, out.moderatorId), 'Moderator assigned.');
        }

        async function decide(outcome) {
            const drops = outcome === 'DROPS';
            if (await confirmDialog({ title: drops ? 'Drop the badge?' : 'Keep the badge?', danger: drops, confirm: drops ? 'Drop it' : 'Keep it',
                text: `${drops ? 'The badge comes off their profile.' : 'The badge stays.'} The removal itself stands either way. This closes the appeal and cannot be undone.` }))
                again(() => api.decideInvestigation(id, outcome), 'Decided. The appeal is closed.');
        }

        async function close() {
            if (await confirmDialog({ title: 'Close this report?', text: 'The reporter is told it was reviewed. The moderator loses access to the Yarnspace. You keep the findings here.', confirm: 'Close it' }))
                again(() => api.closeInvestigation(id), 'Closed.');
        }

        const open = i.status !== 'CLOSED';
        root.replaceChildren(...[
            h('div', { class: 'ad-head' }, h('h2', { text: i.title || 'A Yarnspace' }), h('button', { class: 'ad-btn', type: 'button', text: '← Back', onclick: list })),
            h('div', {}, tag(kind(i), i.kind === 'APPEAL' ? 'is-warn' : ''), tag(STATUS[i.status][0], STATUS[i.status][1]),
                h('span', { class: 'ad-sub', text: ` opened ${ago(i.createdAt)} by ${i.reporter}${i.moderator ? ` · moderator: ${i.moderator}` : ''}` })),
            h('section', { class: 'ad-panel' }, h('h3', { text: i.kind === 'APPEAL' ? 'The appeal' : 'The report' }), h('p', { class: 'ad-quote', text: i.reason }),
                d.members.length ? h('p', { class: 'ad-sub', text: `In this Yarnspace: ${d.members.join(', ')}` }) : null),
            appeal ? appealPanel(appeal) : null,
            findingsPanel(d.findings, appeal != null),
            open ? h('div', { class: 'ad-actions' },
                h('button', { class: `ad-btn${i.moderator ? '' : ' ad-btn--primary'}`, type: 'button', text: i.moderator ? 'Change moderator' : 'Assign a moderator', onclick: assign }),
                appeal ? [h('button', { class: 'ad-btn', type: 'button', text: 'Keep the badge', onclick: () => decide('STICKS') }),
                          h('button', { class: 'ad-btn ad-btn--danger', type: 'button', text: 'Drop the badge', onclick: () => decide('DROPS') })]
                       : h('button', { class: 'ad-btn', type: 'button', text: 'Close report', onclick: close })) : null].filter(Boolean));   // replaceChildren would print a null
    }

    await list();
    // A "queue" signal means look again; the list redraws only if something changed. Without the socket it looks every 15s instead.
    const idle = () => showing === 'list' && document.visibilityState === 'visible' && !document.querySelector('dialog[open]');
    const look = () => { if (idle()) list(true).catch(() => {}); };
    const watch = watchQueue(look);
    const timer = setInterval(() => {
        if (!root.isConnected) { clearInterval(timer); return watch.stop(); }
        if (!watch.connected()) look();
    }, 15_000);
    return root;
}

function appealPanel(a) {
    const r = a.record;
    return h('section', { class: 'ad-panel' }, h('h3', { text: 'The removal' }),
        h('p', { text: `${r.removed.username} was removed from "${r.spaceName}" by ${r.removedBy.username}: ${r.reason}` }),
        a.outcome ? h('p', { class: 'ad-sub', text: `Decided: badge ${a.outcome === 'DROPS' ? 'dropped' : 'stays'}` }) : null,
        h('h3', { text: 'The room, a day before to an hour after' }),
        h('div', { class: 'ad-transcript', tabindex: '0' }, ...(a.history.length ? a.history.map((y) =>
            h('div', { class: `ad-yarn${y.system ? ' is-system' : ''}` }, h('span', { text: y.system ? 'system' : y.sender || '?' }), h('span', { text: y.body }))) : [h('p', { class: 'ad-empty', text: 'No yarns in that window.' })])));
}

function findingsPanel(findings, isAppeal) {
    if (!findings.length) return h('section', { class: 'ad-panel' }, h('h3', { text: 'Findings' }), h('p', { class: 'ad-sub', text: 'Nothing from a moderator yet.' }));
    return h('section', { class: 'ad-panel' }, h('h3', { text: 'Findings' }), ...findings.map((f) => h('div', { class: 'ad-panel' },
        h('div', { class: 'ad-sub', text: `${f.moderator} · ${ago(f.createdAt)}${isAppeal && f.recommendation ? ` · recommends: badge ${f.recommendation === 'DROPS' ? 'drops' : 'stays'}` : ''}` }),
        h('p', { class: 'ad-quote', text: f.text }),
        f.screenshots.length ? h('div', { class: 'ad-thumbs' }, ...f.screenshots.map(thumb)) : null)));
}

/** Loads lazily with the admin key; click opens the full image in a new tab. */
function thumb(id) {
    const b = h('button', { class: 'ad-thumb', type: 'button', text: 'Loading…', title: 'Open full size' });
    api.screenshotUrl(id).then((u) => { b.textContent = ''; b.style.backgroundImage = `url("${u}")`; b.onclick = () => window.open(u, '_blank', 'noopener'); })
        .catch((e) => { b.textContent = e.message; });
    return b;
}
