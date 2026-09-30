// Yarnspaces hub: mock threads + pure helpers. No DOM, no network; swap for js/services/api.js later.
// tier: 'myspace' (1-on-1) | 'wespace' (1-to-many) | 'workspace' (work-group)

export const TIERS = [
    { id: 'all',       label: 'All yarns' },
    { id: 'myspace',   label: 'MySpace' },
    { id: 'wespace',   label: 'WeSpace' },
    { id: 'workspace', label: 'Workspaces' },
];

export function initialThreads() {
    return [
        { id: 'w1', tier: 'workspace', name: 'Kobo', tag: 'Workspace', people: [['Tolu', 32], ['Amaka', 330], ['Kelvin', 200]], last: 'Amaka O. claims ₦30,000 paid to Tolu A.', who: 'System', mins: 6, unread: 3,
          action: { title: 'Payment claim waiting on you', text: 'Amaka O. claims ₦30,000 paid to Tolu A. The room is on hold until you confirm.' }, href: '/HTML-pages/workspace.html#room' },
        { id: 'p1', tier: 'myspace', name: 'Amaka O.', tag: 'Application', people: [['Amaka', 330]], last: 'Your application to “Kobo needs a designer & a backend brain” was accepted.', who: 'Amaka O.', mins: 42, unread: 1 },
        { id: 'e1', tier: 'wespace', name: 'Kobo needs a designer & a backend brain', tag: 'WeSpace', people: [['Tolu', 32], ['Amaka', 330], ['Kelvin', 200], ['Sade', 160]], last: 'Shortlist v2 is up. Two of us disagree on the backend pick.', who: 'Kelvin M.', mins: 95, unread: 4,
          action: { title: 'Decision waiting on you', text: 'The final listing needs general consensus. Reply before the response clock runs out (48h).' } },
        { id: 'p2', tier: 'myspace', name: 'Chidi E.', tag: 'Nudge', people: [['Chidi', 270]], last: 'Tolu A. nudged you: a WeSpace decision is waiting.', who: 'System', mins: 300, unread: 0 },
        { id: 'w2', tier: 'workspace', name: 'Pocket Pilot', tag: 'Workspace', people: [['Sade', 160], ['Tolu', 32]], last: 'Demo build is green. Shipping to testers tonight.', who: 'Sade B.', mins: 1440, unread: 0, href: '/HTML-pages/workspace.html#room' },
        { id: 'e2', tier: 'wespace', name: 'Market Ledger', tag: 'WeSpace', people: [['Kelvin', 200], ['Amaka', 330], ['Chidi', 270]], last: 'Should the ledger be offline first? Voting closes Friday.', who: 'Chidi E.', mins: 2880, unread: 0 },
        { id: 'p3', tier: 'myspace', name: 'Kelvin M.', tag: 'Yarn request', people: [['Kelvin', 200]], last: 'Sent you a yarn request.', who: 'Kelvin M.', mins: 4320, unread: 1 },
    ];
}

export const inTier = (threads, tier) => (tier === 'all' ? threads : threads.filter((t) => t.tier === tier));
export const actionThreads = (threads) => threads.filter((t) => t.action);
export const unreadTotal = (threads) => threads.reduce((n, t) => n + t.unread, 0);
export const markRead = (threads, id) => threads.map((t) => (t.id === id ? { ...t, unread: 0, action: undefined } : t));
export const search = (threads, q) => {
    const s = q.trim().toLowerCase();
    return s ? threads.filter((t) => `${t.name} ${t.last}`.toLowerCase().includes(s)) : threads;
};
export function ago(mins) {
    if (mins < 60) return `${mins}m`;
    if (mins < 1440) return `${Math.round(mins / 60)}h`;
    return `${Math.round(mins / 1440)}d`;
}
