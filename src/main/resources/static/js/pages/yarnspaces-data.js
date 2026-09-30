// Yarnspaces hub: section list + small pure helpers. No DOM, no network (those are in yarnspaces.js and js/services/api.js).

export const SECTIONS = [
    { id: 'all',       label: 'All yarns',  tier: null },
    { id: 'myspace',   label: 'MySpace',    tier: 'MYSPACE' },
    { id: 'wespace',   label: 'WeSpace',    tier: 'WESPACE' },
    { id: 'workspace', label: 'Workspaces', tier: 'WORKSPACE' },
    { id: 'archive',   label: 'Archive',    tier: null },
    { id: 'blocked',   label: 'Blocked',    tier: null },
];

export const TIER_LABEL = { MYSPACE: 'MySpace', WESPACE: 'WeSpace', WORKSPACE: 'Workspace' };

export const inSection = (threads, section) => (section.tier ? threads.filter((t) => t.tier === section.tier) : threads);
export const unreadTotal = (threads) => threads.reduce((n, t) => n + t.unread, 0);

export function matches(thread, text) {
    const s = text.trim().toLowerCase();
    if (!s) return true;
    return [thread.name, thread.lastBody, ...thread.members.map((m) => m.username)].some((v) => (v || '').toLowerCase().includes(s));
}

export function ago(iso, now = Date.now()) {
    const mins = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
    if (mins < 1) return 'now';
    if (mins < 60) return `${mins}m`;
    if (mins < 1440) return `${Math.round(mins / 60)}h`;
    return `${Math.round(mins / 1440)}d`;
}

// Stable colour per person, so the same username always gets the same avatar.
export function hue(name) {
    let n = 0;
    for (const ch of name) n = (n * 31 + ch.charCodeAt(0)) % 360;
    return n;
}
