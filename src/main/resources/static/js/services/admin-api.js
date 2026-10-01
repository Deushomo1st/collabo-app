// Admin console API layer: every admin fetch lives here. The admin key (not the login cookie) is the credential,
// kept in sessionStorage so closing the tab signs you out. See AdminKeyFilter on the server.

const KEY = 'adminKey';
export const getKey = () => { try { return sessionStorage.getItem(KEY) || ''; } catch { return ''; } };
export const setKey = (key) => { try { key ? sessionStorage.setItem(KEY, key) : sessionStorage.removeItem(KEY); } catch { /* private mode: the key lives for this page only */ } };

/** kind: 'wrong' (403) or 'unconfigured' (503, ADMIN_KEY missing on the server). The console answers both with the gate. */
export class AdminAuthError extends Error {
    constructor(kind) {
        super(kind === 'unconfigured' ? 'Admin key not configured on the server.' : 'Wrong admin key.');
        this.kind = kind;
    }
}

async function call(path, { method = 'GET', body } = {}) {
    const headers = { 'X-Admin-Key': getKey() };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const res = await fetch(`/api/admin${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (res.status === 403) throw new AdminAuthError('wrong');
    if (res.status === 503) throw new AdminAuthError('unconfigured');
    const data = res.status === 204 ? null : await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((data && (data.message || data.error)) || `Request failed (${res.status})`);
    return data;
}

export const status = () => call('/status');

export const users = () => call('/users');
export const createUser = (body) => call('/users', { method: 'POST', body });
export const createUsers = (users, test) => call('/users/bulk', { method: 'POST', body: { users, test } });
export const setRole = (id, role) => call(`/users/${id}/role`, { method: 'PATCH', body: { role } });
export const setPremium = (id, premium) => call(`/users/${id}/premium`, { method: 'PATCH', body: { premium } });
export const deleteUser = (id) => call(`/users/${id}`, { method: 'DELETE' });

export const moderators = () => call('/moderators');
export const createModerator = (body) => call('/moderators', { method: 'POST', body });
export const setModeratorActive = (id, active) => call(`/moderators/${id}/active`, { method: 'PATCH', body: { active } });
export const resetModeratorPassword = (id, password) => call(`/moderators/${id}/password`, { method: 'PUT', body: { password } });

export const tables = () => call('/db/tables');
export const tableRows = (name, limit, offset) => call(`/db/tables/${encodeURIComponent(name)}/rows?limit=${limit}&offset=${offset}`);
export const cleanTable = (name) => call(`/db/tables/${encodeURIComponent(name)}/truncate`, { method: 'POST' });

export const reports = () => call('/reports');
export const resolveReport = (id, resolved) => call(`/reports/${id}/resolve`, { method: 'POST', body: { resolved } });

/** A report's screenshot or video: needs the key header, so it comes down as a blob. Returns an object URL. */
export async function reportMediaUrl(reportId, mediaId) {
    const res = await fetch(`/api/admin/reports/${reportId}/media/${mediaId}`, { headers: { 'X-Admin-Key': getKey() } });
    if (res.status === 403) throw new AdminAuthError('wrong');
    if (!res.ok) throw new Error('Could not load the file.');
    return URL.createObjectURL(await res.blob());
}

export const liveTicket = () => call('/live/ticket', { method: 'POST' });
export const investigations = (status = 'active') => call(`/investigations?status=${status}`);
export const investigation = (id) => call(`/investigations/${id}`);
export const assignInvestigation = (id, moderatorId) => call(`/investigations/${id}/assign`, { method: 'POST', body: { moderatorId } });
export const closeInvestigation = (id) => call(`/investigations/${id}/close`, { method: 'POST' });
export const decideInvestigation = (id, outcome) => call(`/investigations/${id}/decide`, { method: 'POST', body: { outcome } });

/** A screenshot needs the admin key header, which an <img src> cannot send, so it comes down as a blob. Returns an object URL. */
export async function screenshotUrl(id) {
    const res = await fetch(`/api/admin/investigations/screenshots/${id}`, { headers: { 'X-Admin-Key': getKey() } });
    if (res.status === 403) throw new AdminAuthError('wrong');
    if (!res.ok) throw new Error('Could not load the screenshot.');
    return URL.createObjectURL(await res.blob());
}
