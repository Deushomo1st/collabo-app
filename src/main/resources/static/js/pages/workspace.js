// Workspace screen: rendering + wiring. State lives in workspace-data.js.
// All text goes in through textContent (h() never sets innerHTML), so user text is safe.
import { fanActions } from '/js/services/fan-actions.js';
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';
import { mountThemeSwitcher, mountThemeRow } from '/js/components/theme-switcher/theme-switcher.js';
import { createActionBanner, preloadActionBanner } from '/js/components/action-banner/action-banner.js';
import { createSwitch, preloadSwitch } from '/js/components/switch/switch.js';
import { openGlassBlurDialog, glassBlurConfirm, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import {
    ME, PERMISSIONS, MIN_CLOCK_HOURS, initialState, memberOf, activeClaim, openTasks, naira,
    sendMessage, addDraftFiles, removeDraftFile, togglePin, toggleTask, toggleTaskMilestone, toggleOptOut, logPayment,
    resolvePayment, setPermission, nudge, changeSetting,
} from '/js/pages/workspace-data.js';

let state = initialState();
let islandOpen = false;

// ---- tiny DOM helper -------------------------------------------------------
function h(tag, props = {}, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'text') el.textContent = v;
        else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
        else if (k === 'style') el.style.cssText = v;
        else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) if (kid != null && kid !== false) el.append(kid);
    return el;
}
const svg = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const icon = (inner, size = 16) => { const s = h('span', { style: `display:inline-grid;width:${size}px;height:${size}px` }); s.innerHTML = svg(inner); return s; };
const ICON = {
    room: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    members: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    flag: '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/>',
    card: '<rect x="1" y="4" width="22" height="16" rx="2"/><line x1="1" y1="10" x2="23" y2="10"/>',
    gear: '<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/>',
    star: '<polygon points="12 2 15.1 8.6 22 9.3 16.8 14 18.2 21 12 17.5 5.8 21 7.2 14 2 9.3 8.9 8.6 12 2"/>',
    pin: '<path d="M12 17v5M9 3h6l-1 7 3 3H7l3-3z"/>',
    chev: '<polyline points="6 9 12 15 18 9"/>',
    back: '<polyline points="15 18 9 12 15 6"/>',
    clip: '<path d="M21.4 11.1l-9.2 9.2a5.5 5.5 0 0 1-7.8-7.8l9.2-9.2a3.7 3.7 0 0 1 5.2 5.2l-9.2 9.2a1.8 1.8 0 0 1-2.6-2.6l8.5-8.5"/>',
    send: '<line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>',
};

const avatar = (m, cls = '') => h('span', { class: `sp-avatar ${cls} ${m.status === 'frozen' ? 'is-frozen' : ''}`, style: `--hue:${m.hue}`, title: m.name, text: m.name[0] });
const who = (id) => memberOf(state, id);

function update(next) { state = next; renderAll(); }
function toast(text) {
    const el = document.getElementById('toast');
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2200);
}

// ---- Header + task island --------------------------------------------------
function renderHeader() {
    const active = state.members.filter((m) => m.status === 'active');
    document.getElementById('header-info').replaceChildren(
        h('div', { class: 'sp-title__row' },
            h('h1', { text: state.space.name }),
            h('span', { class: 'sp-tag sp-tag--brand', text: 'Workspace' })),
        h('p', { text: `${state.space.tagline} · ${active.length} active of ${state.members.length}` }));
    document.getElementById('header-stack').replaceChildren(...state.members.map((m) => avatar(m)));
}

function setIsland(open) {
    islandOpen = open;
    const island = document.getElementById('island');
    island.classList.toggle('is-open', open);
    island.querySelector('.sp-island__pill')?.setAttribute('aria-expanded', String(open));
}

function renderIsland() {
    const open = openTasks(state);
    const latest = open[open.length - 1];   // newest task still open
    const list = h('ul', { class: 'sp-island__list' }, ...state.tasks.map((t) => {
        const a = who(t.assignee);
        return h('li', { class: `sp-task ${t.done ? 'is-done' : ''}` },
            h('label', { class: 'sp-check' },
                h('input', { type: 'checkbox', checked: t.done, 'aria-label': `Mark “${t.title}” fulfilled`, onchange: () => update(toggleTask(state, t.id)) }),
                h('span')),
            h('span', { class: 'sp-task__title', text: t.title }),
            h('button', {
                class: 'sp-star', type: 'button', 'aria-pressed': String(t.milestone), disabled: t.done,
                title: t.milestone ? 'This task will log a milestone' : 'Make this a milestone',
                onclick: () => update(toggleTaskMilestone(state, t.id)),
            }, icon(ICON.star, 16)),
            avatar(a, 'sp-avatar--sm'));
    }));
    const pill = h('button', { class: 'sp-island__pill', type: 'button', 'aria-expanded': String(islandOpen), 'aria-controls': 'island-body', onclick: () => setIsland(!islandOpen) },
        h('span', { class: 'sp-island__dot' }),
        h('span', { class: 'sp-island__text' }, 'Tasks', h('small', { text: `${open.length} open` })),
        h('span', { class: 'sp-island__latest', text: latest ? latest.title : 'All fulfilled' }),
        h('span', { class: 'sp-island__chev' }, icon(ICON.chev, 16)));
    const island = document.getElementById('island');
    island.className = 'sp-island' + (islandOpen ? ' is-open' : '');
    island.replaceChildren(pill, h('div', { class: 'sp-island__body', id: 'island-body' }, h('div', { class: 'sp-island__inner' }, list)));
    island.querySelectorAll('.sp-star[aria-pressed="true"] svg').forEach((el) => el.setAttribute('fill', 'currentColor'));
}

// ---- Room ------------------------------------------------------------------
function renderRoom(root) {
    const claim = activeClaim(state);
    const parts = [];
    if (claim) {
        const mine = claim.recipient === ME, payer = claim.payer === ME;
        parts.push(createActionBanner({
            title: `${who(claim.payer).name} claims ${naira(claim.amount)} paid to ${who(claim.recipient).name}`,
            text: `“${claim.note}” · Until confirmed this is only a claim, and the room is on hold.`,
            actions: [
                mine && { label: 'Confirm receipt', variant: 'ok', onClick: () => { update(resolvePayment(state, claim.id, 'confirmed')); toast('Receipt confirmed. Room reopened.'); } },
                payer && { label: 'Cancel claim', variant: 'danger', onClick: () => { update(resolvePayment(state, claim.id, 'cancelled')); toast('Claim cancelled.'); } },
            ].filter(Boolean),
            note: !mine && !payer ? 'Waiting on the recipient' : undefined,
        }).element);
    }
    const pinned = state.messages.filter((m) => m.pinned);
    const canPin = who(ME).perms.includes('pin');
    const msgs = state.messages.map((m) => {
        if (m.type === 'day') return h('div', { class: 'sp-day', text: m.text });
        if (m.type === 'system') return h('div', { class: 'sp-sys' }, m.text, h('time', { text: m.time }));
        const a = who(m.who), me = m.who === ME;
        return h('div', { class: `sp-msg ${me ? 'is-me' : ''}` }, avatar(a, 'sp-avatar--sm'),
            h('div', { class: 'sp-bubble' },
                m.pinned && h('span', { class: 'sp-pin', text: 'PINNED' }),
                h('div', { class: 'sp-bubble__who' }, a.name, h('small', { text: a.title }),
                    canPin && h('button', { class: 'sp-pinbtn', type: 'button', title: m.pinned ? 'Unpin' : 'Pin', 'aria-label': m.pinned ? 'Unpin message' : 'Pin message', onclick: () => update(togglePin(state, m.id)) }, icon(ICON.pin, 13))),
                m.text && h('p', { text: m.text }),
                m.files?.length && h('div', { class: 'sp-files' }, ...m.files.map((f) => fileChip(f))),
                h('time', { text: m.time })));
    });
    const input = h('input', {
        type: 'text', placeholder: 'Send a yarn to the room', 'aria-label': 'Send a yarn to the room', maxlength: '500', disabled: !!claim, value: state.draft,
        oninput: (e) => { state = { ...state, draft: e.target.value }; },
        onkeydown: (e) => { if (e.key === 'Enter') send(); },
    });
    const send = () => {
        const next = sendMessage(state, input.value);
        if (next === state) return;
        update(next);
        requestAnimationFrame(() => { document.querySelector('.sp-composer input')?.focus(); window.scrollTo({ top: document.body.scrollHeight }); });
    };
    const picker = h('input', {
        type: 'file', multiple: true, hidden: true,
        onchange: (e) => {
            const limit = state.settings.attachMB * 1024 * 1024;
            const files = [...e.target.files], ok = files.filter((f) => f.size <= limit);
            if (ok.length < files.length) toast(`Skipped ${files.length - ok.length} over the ${state.settings.attachMB} MB ceiling.`);
            if (ok.length) update(addDraftFiles(state, ok));
        },
    });
    const chips = state.draftFiles.length
        ? h('div', { class: 'sp-files sp-files--draft' }, ...state.draftFiles.map((f, i) => fileChip(f, () => update(removeDraftFile(state, i)))))
        : null;
    root.append(
        h('h2', { class: 'sp-h2', text: 'Room' }),
        h('p', { class: 'sp-sub', text: `${state.space.name} Workspace · ${pinned.length} pinned · settings changes and removals are announced here` }),
        ...parts,
        h('div', { class: 'sp-room sp-glass' }, ...msgs),
        h('div', { class: 'sp-composer sp-glass' },
            chips,
            h('div', { class: 'sp-composer__row' },
                h('button', { class: 'sp-iconbtn', type: 'button', disabled: !!claim, title: 'Attach files', 'aria-label': 'Attach files', onclick: () => picker.click() }, icon(ICON.clip, 20)),
                picker,
                claim ? h('span', { class: 'sp-composer__locked', text: 'Room on hold: resolve the payment claim above to keep talking.' }) : input,
                h('button', { class: 'sp-iconbtn sp-iconbtn--send', type: 'button', disabled: !!claim, title: 'Send (Enter)', 'aria-label': 'Send yarn', onclick: send }, icon(ICON.send, 20)))));
}

const fmtSize = (n) => (n >= 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB');
function fileChip(f, onRemove) {
    return h('span', { class: 'sp-file' }, icon(ICON.clip, 13), h('span', { class: 'sp-file__name', text: f.name }), h('small', { text: fmtSize(f.size) }),
        onRemove && h('button', { class: 'sp-file__x', type: 'button', 'aria-label': `Remove ${f.name}`, onclick: onRemove }, '×'));
}

// ---- Members ---------------------------------------------------------------
function renderMembers(root) {
    root.append(
        h('h2', { class: 'sp-h2', text: 'Members' }),
        h('p', { class: 'sp-sub', text: 'Titles show above each face. Open a member to see their profile and permissions.' }),
        h('div', { class: 'sp-grid' }, ...state.members.map((m) =>
            h('button', { class: 'sp-card sp-glass', type: 'button', onclick: () => openMemberTablet(m.id), 'aria-label': `Open ${m.name}` },
                h('span', { class: `sp-role ${m.status === 'frozen' ? 'is-frozen' : ''}`, text: m.status === 'frozen' ? 'Frozen' : m.title }),
                avatar(m, 'sp-avatar--lg'),
                h('h3', { text: m.name }),
                h('small', { text: m.kind === 'owner' ? 'Owner' : m.kind === 'collaborator' ? 'Collaborator' : 'Member' }),
                h('span', { class: 'sp-perm-dots', title: `${m.perms.length} of ${PERMISSIONS.length} permissions` }, ...PERMISSIONS.map((p) => h('i', { class: m.perms.includes(p.id) ? 'on' : '' }))))))
    );
}

async function openMemberTablet(id) {
    const m = who(id);
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: m.name, html: '<div class="sp-tablet"></div>' });
    const tablet = panel.querySelector('.sp-tablet');
    const boxes = PERMISSIONS.map((p) => {
        const on = m.perms.includes(p.id), locked = m.kind === 'owner';
        const sw = createSwitch({
            checked: on, disabled: locked, label: p.label,
            onChange: async (checked) => {
                if (checked && p.restricted && !(await glassBlurConfirm(`“${p.label}” lets ${m.name.split(' ')[0]} reshape the space. Delegating settings or removals is how a space gets quietly captured. Grant it anyway?`, { title: 'Grant a restricted permission?', okText: 'Grant', danger: true }))) { sw.setChecked(false); return; }
                update(setPermission(state, id, p.id, checked));
            },
        });
        return h('label', { class: 'sp-perm' }, h('span', {}, p.label, h('em', { text: p.restricted ? 'Owner or collaborators only, with a warning' : 'Freely delegable' })), sw.element);
    });
    const quiet = m.quiet >= 3;
    tablet.append(
        h('div', { class: 'sp-tablet__head' }, avatar(m, 'sp-avatar--lg'),
            h('div', {}, h('h3', { class: 'glass-blur-dialog__title', text: m.name }),
                h('div', { text: `${m.title} · ${m.kind}${quiet ? ` · quiet for ${m.quiet} days` : ''}`, style: 'opacity:.7;font-size:13px' }))),
        ...boxes,
        h('div', { class: 'glass-blur-dialog__actions' },
            quiet && m.status !== 'frozen' && h('button', { class: 'glass-blur-dialog__btn glass-blur-dialog__btn--ghost', type: 'button', onclick: () => { update(nudge(state, id)); toast(`Nudge sent to ${m.name.split(' ')[0]}.`); close(); } }, 'Send nudge'),
            h('button', { class: 'glass-blur-dialog__btn', type: 'button', onclick: close }, 'Done')));
}

// ---- Milestones ------------------------------------------------------------
function renderMilestones(root) {
    root.append(
        h('h2', { class: 'sp-h2', text: 'Milestones' }),
        h('p', { class: 'sp-sub', text: 'The space’s record, closer to a commit history than a status. Everyone in the room is credited automatically; you can only remove yourself.' }),
        h('ol', { class: 'sp-timeline' }, ...state.milestones.map((ms) => {
            const out = ms.optedOut.includes(ME), stamped = ms.stamped.includes(ME);
            return h('li', { class: 'sp-ms sp-glass' },
                h('h3', { text: ms.title }), h('p', { text: ms.note }),
                h('div', { class: 'sp-ms__foot' },
                    h('div', { class: 'sp-stack' }, ...ms.stamped.map((id) => avatar(who(id), ms.optedOut.includes(id) ? 'sp-avatar--sm is-out' : 'sp-avatar--sm'))),
                    h('time', { text: `${ms.date} · ${ms.stamped.length - ms.optedOut.length} credited` }),
                    stamped && h('button', { class: 'sp-btn', type: 'button', style: 'margin-left:auto', onclick: () => update(toggleOptOut(state, ms.id)) }, out ? 'Add me back' : 'Remove me')));
        })));
}

// ---- Payments --------------------------------------------------------------
const STATE_TAG = { claimed: ['sp-tag--warn', 'Claimed'], confirmed: ['sp-tag--ok', 'Confirmed'], cancelled: ['sp-tag--muted', 'Cancelled'] };
function renderPayments(root) {
    const claim = activeClaim(state);
    root.append(
        h('div', { style: 'display:flex;align-items:flex-start;gap:12px;flex-wrap:wrap' },
            h('div', { style: 'flex:1 1 260px' }, h('h2', { class: 'sp-h2', text: 'Payment records' }),
                h('p', { class: 'sp-sub', text: 'Documented here, settled elsewhere. COLLABO never moves the money.' })),
            h('button', { class: 'sp-btn sp-btn--brand', type: 'button', disabled: !!claim, title: claim ? 'Resolve the open claim first' : '', onclick: openBillingForm }, '+ Log a payment')),
        ...state.payments.map((p) => {
            const [cls, label] = STATE_TAG[p.state];
            return h('div', { class: 'sp-pay sp-glass' }, avatar(who(p.payer)),
                h('div', { class: 'sp-pay__main' }, h('strong', { text: naira(p.amount) }), h('div', { text: `${who(p.payer).name.split(' ')[0]} → ${who(p.recipient).name.split(' ')[0]} · ${p.note} · ${p.date}` })),
                h('span', { class: `sp-tag ${cls}`, text: label }));
        }),
        h('div', { class: 'sp-fine', text: 'A confirmed entry proves both people said money moved. It is not evidence that it did. Until the recipient confirms, an entry reads “claimed”.' }));
}

async function openBillingForm() {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Log a payment', html: '<h3 class="glass-blur-dialog__title">Log a payment</h3><form class="sp-form"></form>' });
    const form = panel.querySelector('.sp-form');
    const to = h('select', { class: 'sp-input', id: 'pay-to' }, ...state.members.filter((m) => m.id !== ME).map((m) => h('option', { value: m.id, text: m.name })));
    const amount = h('input', { class: 'sp-input', id: 'pay-amt', type: 'number', min: '1', step: '1', placeholder: '25000', required: true });
    const note = h('input', { class: 'sp-input', id: 'pay-note', type: 'text', maxlength: '80', placeholder: 'What was it for?', required: true });
    form.append(
        h('label', { for: 'pay-to' }, 'Paid to', to), h('label', { for: 'pay-amt' }, 'Amount (₦)', amount), h('label', { for: 'pay-note' }, 'Note', note),
        h('div', { class: 'glass-blur-dialog__actions' },
            h('button', { class: 'glass-blur-dialog__btn glass-blur-dialog__btn--ghost', type: 'button', onclick: close }, 'Cancel'),
            h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Mark as paid')));
    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const value = Math.floor(Number(amount.value));
        if (!(value > 0) || !note.value.trim()) return;
        update(logPayment(state, { recipient: to.value, amount: value, note: note.value.trim() }));
        close(); toast('Logged as a claim. The room is on hold.'); location.hash = '#room';
    });
}

// ---- Settings --------------------------------------------------------------
function renderSettings(root) {
    const s = state.settings;
    const clock = h('input', { class: 'sp-input', id: 'clock', type: 'number', min: String(MIN_CLOCK_HOURS), step: '1', value: String(s.clockHours), 'aria-label': 'Response clock in hours' });
    clock.addEventListener('change', () => {
        const next = changeSetting(state, 'clockHours', clock.value);
        if (Number(clock.value) < MIN_CLOCK_HOURS) toast(`Minimum is ${MIN_CLOCK_HOURS} hours, so the clock can’t be used to purge people.`);
        update(next);
    });
    const pleas = createSwitch({ checked: s.pleas, label: 'Pleas', onChange: (on) => update(changeSetting(state, 'pleas', on)) });
    const row = (title, text, control) => h('div', { class: 'sp-setting sp-glass' }, h('div', { class: 'sp-setting__text' }, h('strong', { text: title }), h('span', { text })), control);
    const themeSlot = h('div', { class: 'sp-setting sp-glass' });
    root.append(
        h('h2', { class: 'sp-h2', text: 'Space settings' }),
        h('p', { class: 'sp-sub', text: 'Every change is announced in the room. That is the difference between governance and manipulation.' }),
        row('Response clock', `How long a quiet member has to answer a nudge. Hard floor of ${MIN_CLOCK_HOURS} hours. Applicants see it before they join.`, h('div', {}, clock, h('span', { text: ' hours', style: 'color:var(--sp-muted)' }))),
        row('Pleas', 'A plea buys a quiet member 12 hours. One at a time, one per person per week. Switch off for hard external deadlines.', pleas.element),
        row('Formed', state.space.born, h('span', { class: 'sp-tag', text: state.space.formed })),
        row('Member capacity', 'Larger spaces sit behind a higher plan.', h('span', { class: 'sp-tag', text: `${state.members.length} / ${state.space.capacity} · Free` })),
        row('Attachment ceiling', 'Per milestone, on the current plan.', h('span', { class: 'sp-tag', text: `${s.attachMB} MB` })),
        themeSlot);
    mountThemeRow(themeSlot);
}

// ---- Router ----------------------------------------------------------------
const SECTIONS = [
    { id: 'room', title: 'Room', icon: svg(ICON.room), render: renderRoom },
    { id: 'members', title: 'Members', icon: svg(ICON.members), render: renderMembers },
    { id: 'milestones', title: 'Milestones', icon: svg(ICON.flag), render: renderMilestones },
    { id: 'payments', title: 'Payments', icon: svg(ICON.card), render: renderPayments },
    { id: 'settings', title: 'Settings', icon: svg(ICON.gear), render: renderSettings },
];
const currentId = () => (SECTIONS.find((s) => location.hash.endsWith('#' + s.id)) || SECTIONS[0]).id;
let nav;

function renderSection() {
    const sec = SECTIONS.find((s) => s.id === currentId());
    const root = document.getElementById('section');
    const keepAnim = root.dataset.id !== sec.id;
    root.dataset.id = sec.id;
    root.className = 'sp-section' + (keepAnim ? '' : ' sp-section--still');
    if (!keepAnim) root.style.animation = 'none'; else root.style.animation = '';
    root.replaceChildren();
    sec.render(root);
}
// Room opens at the newest message; every other section opens at the top.
function goSection() {
    nav?.setActive(SECTIONS.findIndex((s) => s.id === currentId()));
    renderSection();
    window.scrollTo({ top: currentId() === 'room' ? document.documentElement.scrollHeight : 0, behavior: 'instant' });
}
function renderAll() { renderHeader(); renderIsland(); renderSection(); }

async function boot() {
    preloadGlassBlurDialog();
    await Promise.all([preloadActionBanner(), preloadSwitch()]);
    renderAll();
    await mountThemeSwitcher('#theme-slot', { inline: true, collapse: true });
    nav = await mountNavSelector('#nav', {
        placement: 'bottom', links: SECTIONS.map((s) => s.title), hrefs: SECTIONS.map((s) => '#' + s.id), icons: SECTIONS.map((s) => s.icon),
        activeIndex: SECTIONS.findIndex((s) => s.id === currentId()), collapseWhenIdle: true, idleMs: 0, holdActions: fanActions(),
        onChange: (_l, href) => {   // href can arrive absolute
            const hash = href.slice(href.lastIndexOf('#'));
            if (location.hash === hash) goSection(); else location.hash = hash;
        },
    });
    const shell = document.querySelector('.sp-shell');   // hysteresis so the height change can't flicker the state
    let lastY = window.scrollY;
    window.addEventListener('scroll', () => {
        const y = window.scrollY;
        if (y > 70) shell.classList.add('is-compact'); else if (y < 10) shell.classList.remove('is-compact');
        if (islandOpen && Math.abs(y - lastY) > 12) setIsland(false);   // tasks close on scroll, next click reopens
        lastY = y;
    }, { passive: true });
    // Header shortcuts: the faces open Members, the rest of the panel opens Settings.
    const header = document.querySelector('.sp-header');
    const go = (e) => {
        if (e.target.closest('.sp-back, #theme-slot')) return;
        const hash = e.target.closest('#header-stack') ? '#members' : '#settings';
        if (location.hash === hash) goSection(); else location.hash = hash;
    };
    header.addEventListener('click', go);
    header.addEventListener('keydown', (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('#header-stack, #header-info')) { e.preventDefault(); go(e); } });
    window.addEventListener('hashchange', goSection);
    goSection();
}
boot();
