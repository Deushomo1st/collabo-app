// API service layer — ALL fetch() calls live here.
// Single source of truth: when auth tokens arrive later, they attach in
// ONE place and every component instantly becomes secure.

async function parseError(response, data, fallback) {
    const err = new Error(data.message || fallback);
    err.status = response.status;
    err.challenge = data.challenge || null;
    err.challengeToken = data.challengeToken || null;
    err.retryAfterSeconds = data.retryAfterSeconds || 0;
    err.requiresVerification = data.requiresVerification || false;
    return err;
}

export async function registerUser(userData) {
    const response = await fetch('/api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userData)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw await parseError(response, data, 'Failed to create account. Please try again.');
    }
    return data;
}

export async function verifyOtp(email, code) {
    const response = await fetch('/api/users/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, code })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw await parseError(response, data, 'Verification failed. Please try again.');
    }
    return data;
}

export async function resendOtp(email) {
    const response = await fetch('/api/users/resend-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw await parseError(response, data, 'Could not resend the code. Please try again.');
    }
    return data;
}

// ---- Yarnspaces (chat) -----------------------------------------------------
// No login yet: the caller names themselves with X-Dev-User. When real auth
// arrives, swap the header for the token here and nothing else changes.
const DEV_USER_KEY = 'collaboDevUser';
export function getDevUser() { try { return localStorage.getItem(DEV_USER_KEY) || ''; } catch { return ''; } }
export function setDevUser(name) { try { if (name) localStorage.setItem(DEV_USER_KEY, name); else localStorage.removeItem(DEV_USER_KEY); } catch { /* storage blocked */ } }

async function yarnCall(path, { method = 'GET', body } = {}) {
    const headers = { 'X-Dev-User': getDevUser() };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch(`/api/yarns${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (response.status === 204) return null;
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw await parseError(response, data, 'Something went wrong. Please try again.');
    return data;
}
const q = (params) => {
    const s = new URLSearchParams(Object.entries(params).filter(([, v]) => v != null && v !== '')).toString();
    return s ? `?${s}` : '';
};

export const yarnMe = () => yarnCall('/me');
export const yarnDirectory = (text) => yarnCall(`/directory${q({ q: text })}`);
export const yarnThreads = (view = 'inbox', tier, text) => yarnCall(`/threads${q({ view, tier, q: text })}`);
export const yarnStartMySpace = (username, body) => yarnCall('/threads/myspace', { method: 'POST', body: { username, body } });
export const yarnCreateGroup = (tier, name, usernames) => yarnCall('/threads/group', { method: 'POST', body: { tier, name, usernames } });
export const yarnHistory = (id, before) => yarnCall(`/threads/${id}/yarns${q({ before, limit: 50 })}`);
export const yarnSend = (id, body) => yarnCall(`/threads/${id}/yarns`, { method: 'POST', body: { body } });
export const yarnMarkRead = (id) => yarnCall(`/threads/${id}/read`, { method: 'POST' });
export const yarnPrefs = (id, prefs) => yarnCall(`/threads/${id}/prefs`, { method: 'PATCH', body: prefs });
export const yarnRespond = (id, accept) => yarnCall(`/threads/${id}/respond`, { method: 'POST', body: { accept } });
export const yarnBlocked = () => yarnCall('/blocks');
export const yarnBlock = (userId) => yarnCall(`/blocks/${userId}`, { method: 'PUT' });
export const yarnUnblock = (userId) => yarnCall(`/blocks/${userId}`, { method: 'DELETE' });
