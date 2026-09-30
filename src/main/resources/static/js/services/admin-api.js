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
