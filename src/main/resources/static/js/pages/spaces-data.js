// Mock data + pure state transitions for the Spaces (Workspace) screen.
// Nothing here touches the DOM or the network; every function returns a NEW state.
// When the backend exists, swap `initialState()` for a call in js/services/api.js.

export const ME = 'tolu';

export const PERMISSIONS = [
    { id: 'milestones', label: 'Log and fulfil milestones', restricted: false },
    { id: 'payments',   label: 'Log payment records',       restricted: false },
    { id: 'members',    label: 'Accept and remove members', restricted: true },
    { id: 'settings',   label: 'Edit space settings',       restricted: true },
    { id: 'pin',        label: 'Post and pin in the room',  restricted: true },
    { id: 'recruit',    label: 'Manage re-recruitment posts', restricted: true },
];

const ALL = PERMISSIONS.map((p) => p.id);
export const MIN_CLOCK_HOURS = 48;

export function initialState() {
    return {
        space: { name: 'Kobo', tagline: 'Micro-savings for market traders', born: 'Born from the post “Kobo needs a designer & a backend brain”', formed: 'Sep 23, 2026', capacity: 8 },
        members: [
            { id: 'tolu',   name: 'Tolu A.',   title: 'Product lead',  hue: 32,  kind: 'owner',        status: 'active', quiet: 0, perms: ALL },
            { id: 'amaka',  name: 'Amaka O.',  title: 'Designer',      hue: 330, kind: 'collaborator', status: 'active', quiet: 0, perms: ['milestones', 'payments', 'members', 'pin'] },
            { id: 'kelvin', name: 'Kelvin M.', title: 'Backend',       hue: 200, kind: 'member',       status: 'active', quiet: 1, perms: ['milestones'] },
            { id: 'sade',   name: 'Sade B.',   title: 'Frontend',      hue: 160, kind: 'member',       status: 'active', quiet: 0, perms: ['payments'] },
            { id: 'chidi',  name: 'Chidi E.',  title: 'Growth',        hue: 270, kind: 'member',       status: 'frozen', quiet: 9, perms: [] },
        ],
        messages: [
            { id: 1, type: 'day', text: 'Monday' },
            { id: 2, type: 'system', text: 'Workspace opened from the post “Kobo needs a designer & a backend brain”. Name inherited from the We Space.', time: '09:02' },
            { id: 3, type: 'msg', who: 'amaka', text: 'Onboarding flow is in Figma. Three screens, no more. Comment on anything that feels heavy.', time: '09:40', pinned: true },
            { id: 4, type: 'msg', who: 'kelvin', text: 'Deposits endpoint is up on staging. Idempotency keys are in, so a double tap can’t double-save.', time: '11:15' },
            { id: 5, type: 'msg', who: 'sade', text: 'Wiring the savings ring to it now. Ring animates on real numbers, not fake ones.', time: '11:32' },
            { id: 6, type: 'system', text: 'Tolu A. froze Chidi E. after no reply to a nudge for 9 days.', time: '13:00' },
            { id: 7, type: 'msg', who: 'tolu', text: 'Demo for the pilot traders is Friday. Anything not on the board by Wednesday is out.', time: '14:20' },
        ],
        tasks: [
            { id: 't1', title: 'Savings ring animation', assignee: 'sade',   done: false, milestone: false },
            { id: 't2', title: 'Idempotent deposit endpoint', assignee: 'kelvin', done: true,  milestone: false },
            { id: 't3', title: 'Onboarding: three screens', assignee: 'amaka',  done: true,  milestone: false },
            { id: 't4', title: 'Pilot build live for Friday demo', assignee: 'tolu', done: false, milestone: true },
            { id: 't5', title: 'Trader interview script', assignee: 'chidi', done: false, milestone: false },
        ],
        milestones: [
            { id: 'm1', title: 'Clickable prototype approved', note: 'Five traders tapped through it at the Balogun market visit. Three signed up for the pilot.', date: 'Sep 24', stamped: ['tolu', 'amaka', 'kelvin', 'sade', 'chidi'], optedOut: ['chidi'] },
            { id: 'm2', title: 'Staging environment running', note: 'Deposits, withdrawals and the OTP flow work end to end.', date: 'Sep 27', stamped: ['tolu', 'amaka', 'kelvin', 'sade'], optedOut: [] },
        ],
        payments: [
            { id: 'p1', payer: 'tolu', recipient: 'kelvin', amount: 45000, note: 'Server credits for September', state: 'confirmed', date: 'Sep 26' },
            { id: 'p2', payer: 'amaka', recipient: 'tolu', amount: 30000, note: 'Prototype-day transport & printing', state: 'claimed', date: 'Today' },
        ],
        settings: { clockHours: 72, pleas: true, attachMB: 25 },
        draft: '',
        draftFiles: [],
        nextId: 100,
    };
}

// ---- helpers -------------------------------------------------------------

export const memberOf = (state, id) => state.members.find((m) => m.id === id);
export const activeClaim = (state) => state.payments.find((p) => p.state === 'claimed');
export const openTasks = (state) => state.tasks.filter((t) => !t.done);

const clock = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
const withId = (state) => [state.nextId, { ...state, nextId: state.nextId + 1 }];

function announce(state, text) {
    const [id, next] = withId(state);
    return { ...next, messages: [...next.messages, { id, type: 'system', text, time: clock() }] };
}

const first = (state, id) => memberOf(state, id).name.split(' ')[0];
const naira = (n) => '₦' + n.toLocaleString('en-NG');
export { naira };

// ---- transitions ---------------------------------------------------------

// Attachments are name + size only for now; upload goes through api.js once the backend exists.
export function addDraftFiles(state, files) {
    return { ...state, draftFiles: [...state.draftFiles, ...files.map(({ name, size }) => ({ name, size }))] };
}

export function removeDraftFile(state, index) {
    return { ...state, draftFiles: state.draftFiles.filter((_, i) => i !== index) };
}

export function sendMessage(state, text) {
    const body = text.trim();
    if ((!body && !state.draftFiles.length) || activeClaim(state)) return state;
    const [id, next] = withId(state);
    const msg = { id, type: 'msg', who: ME, text: body, time: clock(), files: state.draftFiles };
    return { ...next, draft: '', draftFiles: [], messages: [...next.messages, msg] };
}

export function togglePin(state, msgId) {
    return { ...state, messages: state.messages.map((m) => (m.id === msgId ? { ...m, pinned: !m.pinned } : m)) };
}

export function toggleTaskMilestone(state, taskId) {
    return { ...state, tasks: state.tasks.map((t) => (t.id === taskId && !t.done ? { ...t, milestone: !t.milestone } : t)) };
}

// Checking a task off. A milestone-flagged task also logs a milestone,
// stamped automatically with every active member (nobody is written out by hand).
export function toggleTask(state, taskId) {
    const task = state.tasks.find((t) => t.id === taskId);
    if (!task) return state;
    const done = !task.done;
    let next = { ...state, tasks: state.tasks.map((t) => (t.id === taskId ? { ...t, done } : t)) };
    if (done && task.milestone) {
        const [id, withCounter] = withId(next);
        const stamped = withCounter.members.filter((m) => m.status === 'active').map((m) => m.id);
        const milestone = { id: 'm' + id, title: task.title, note: 'Fulfilled from the task board.', date: 'Today', stamped, optedOut: [] };
        next = announce({ ...withCounter, milestones: [milestone, ...withCounter.milestones] }, `Milestone fulfilled: “${task.title}”. ${stamped.length} members credited.`);
    }
    return next;
}

export function toggleOptOut(state, milestoneId) {
    const ms = state.milestones.find((m) => m.id === milestoneId);
    if (!ms) return state;
    const out = ms.optedOut.includes(ME);
    const optedOut = out ? ms.optedOut.filter((id) => id !== ME) : [...ms.optedOut, ME];
    return { ...state, milestones: state.milestones.map((m) => (m.id === milestoneId ? { ...m, optedOut } : m)) };
}

export function logPayment(state, { recipient, amount, note }) {
    if (activeClaim(state)) return state;
    const [id, next] = withId(state);
    const record = { id: 'p' + id, payer: ME, recipient, amount, note, state: 'claimed', date: 'Today' };
    return announce({ ...next, payments: [record, ...next.payments] },
        `${first(next, ME)} logged a payment of ${naira(amount)} to ${first(next, recipient)}. Room on hold until it is confirmed or cancelled.`);
}

export function resolvePayment(state, paymentId, outcome) {
    const p = state.payments.find((x) => x.id === paymentId);
    if (!p || p.state !== 'claimed') return state;
    const payments = state.payments.map((x) => (x.id === paymentId ? { ...x, state: outcome } : x));
    const verb = outcome === 'confirmed' ? `${first(state, p.recipient)} confirmed receiving ${naira(p.amount)} from ${first(state, p.payer)}.`
                                         : `${first(state, p.payer)} cancelled the ${naira(p.amount)} claim.`;
    return announce({ ...state, payments }, verb + ' Room is open again.');
}

export function setPermission(state, memberId, permId, on) {
    const who = memberOf(state, memberId);
    if (!who || who.kind === 'owner') return state;
    const perms = on ? [...new Set([...who.perms, permId])] : who.perms.filter((p) => p !== permId);
    const label = PERMISSIONS.find((p) => p.id === permId).label.toLowerCase();
    const members = state.members.map((m) => (m.id === memberId ? { ...m, perms } : m));
    return announce({ ...state, members }, `${first(state, ME)} ${on ? 'granted' : 'removed'} “${label}” ${on ? 'to' : 'from'} ${first(state, memberId)}.`);
}

export function nudge(state, memberId) {
    return announce(state, `${first(state, ME)} nudged ${first(state, memberId)}. It lands in their My Space with a pointer to what’s waiting.`);
}

export function changeSetting(state, key, value) {
    if (key === 'clockHours') {
        const hours = Math.max(MIN_CLOCK_HOURS, Math.round(Number(value) || MIN_CLOCK_HOURS));
        if (hours === state.settings.clockHours) return state;
        return announce({ ...state, settings: { ...state.settings, clockHours: hours } }, `${first(state, ME)} set the response clock to ${hours} hours.`);
    }
    if (key === 'pleas') {
        return announce({ ...state, settings: { ...state.settings, pleas: !!value } }, `${first(state, ME)} turned pleas ${value ? 'on' : 'off'}.`);
    }
    return state;
}
