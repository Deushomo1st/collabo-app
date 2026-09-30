// API service layer — ALL fetch() calls live here.
// Single source of truth: the login cookie and the CSRF token are handled in
// ONE place (send()), so every component is secure without knowing about either.
//
// The login session is an HttpOnly cookie the browser sends by itself; scripts never see it.
// The CSRF token is a readable cookie (XSRF-TOKEN) that we echo back in X-XSRF-TOKEN on writes.

async function parseError(response, data, fallback) {
    const err = new Error(data.message || fallback);
    err.status = response.status;
    err.challenge = data.challenge || null;
    err.challengeToken = data.challengeToken || null;
    err.retryAfterSeconds = data.retryAfterSeconds || 0;
    err.requiresVerification = data.requiresVerification || false;
    return err;
}

const csrfCookie = () => {
    const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return m ? decodeURIComponent(m[1]) : '';
};

// fetch + JSON + CSRF. Returns { response, data }. A write that is refused for a stale CSRF token is retried once.
async function send(path, { method = 'GET', body } = {}, retry = true) {
    const write = method !== 'GET';
    if (write && !csrfCookie()) await fetch('/api/auth/csrf');
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (write) headers['X-XSRF-TOKEN'] = csrfCookie();
    const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (response.status === 403 && write && retry) {   // token expired or rotated: fetch a fresh one and try again
        await fetch('/api/auth/csrf');
        return send(path, { method, body }, false);
    }
    const data = response.status === 204 ? null : await response.json().catch(() => ({}));
    return { response, data };
}

// The same fetch, but throws on failure with the server's message.
async function call(path, options, fallback) {
    const { response, data } = await send(path, options);
    if (!response.ok) throw await parseError(response, data || {}, fallback || 'Something went wrong. Please try again.');
    return data;
}

// ---- Accounts --------------------------------------------------------------
export const registerUser = (userData) => call('/api/users', { method: 'POST', body: userData }, 'Failed to create account. Please try again.');
export const verifyOtp = (email, code) => call('/api/users/verify', { method: 'POST', body: { email, code } }, 'Verification failed. Please try again.');
export const resendOtp = (email) => call('/api/users/resend-otp', { method: 'POST', body: { email } }, 'Could not resend the code. Please try again.');

// ---- Login session ---------------------------------------------------------
export const loginUser = (identifier, password) => call('/api/auth/login', { method: 'POST', body: { identifier, password } }, 'Could not sign in. Please try again.');
export const logoutUser = () => call('/api/auth/logout', { method: 'POST' });
// The signed-in account, or null when signed out (not an error: the page decides what to show).
export async function currentUser() {
    const { response, data } = await send('/api/auth/me');
    return response.ok ? data : null;
}

// ---- Yarnspaces (chat) -----------------------------------------------------
const yarn = (path, options) => call(`/api/yarns${path}`, options);
const q = (params) => {
    const s = new URLSearchParams(Object.entries(params).filter(([, v]) => v != null && v !== '')).toString();
    return s ? `?${s}` : '';
};

export const yarnMe = currentUser;
export const yarnDirectory = (text) => yarn(`/directory${q({ q: text })}`);
export const yarnThreads = (view = 'inbox', tier, text) => yarn(`/threads${q({ view, tier, q: text })}`);
export const yarnStartMySpace = (username, body) => yarn('/threads/myspace', { method: 'POST', body: { username, body } });
export const yarnCreateGroup = (tier, name, usernames) => yarn('/threads/group', { method: 'POST', body: { tier, name, usernames } });
export const yarnHistory = (id, before) => yarn(`/threads/${id}/yarns${q({ before, limit: 50 })}`);
export const yarnSend = (id, body) => yarn(`/threads/${id}/yarns`, { method: 'POST', body: { body } });
export const yarnMarkRead = (id) => yarn(`/threads/${id}/read`, { method: 'POST' });
export const yarnPrefs = (id, prefs) => yarn(`/threads/${id}/prefs`, { method: 'PATCH', body: prefs });
export const yarnRespond = (id, accept) => yarn(`/threads/${id}/respond`, { method: 'POST', body: { accept } });
export const yarnBlocked = () => yarn('/blocks');
export const yarnBlock = (userId) => yarn(`/blocks/${userId}`, { method: 'PUT' });
export const yarnUnblock = (userId) => yarn(`/blocks/${userId}`, { method: 'DELETE' });
