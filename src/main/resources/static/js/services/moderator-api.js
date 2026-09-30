// Moderator desk API layer: every moderator fetch lives here. A moderator signs in with the same kind of session cookie as a
// user (HttpOnly, sent by the browser) and echoes the CSRF cookie on writes. It is a different kind of account; user pages never see it.

const csrfCookie = () => {
    const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return m ? decodeURIComponent(m[1]) : '';
};

/** status 401 means "not signed in (any more)": the desk answers with the sign-in screen. */
async function call(path, { method = 'GET', body } = {}) {
    const write = method !== 'GET';
    if (write && !csrfCookie()) await fetch('/api/auth/csrf');
    const headers = {};
    const raw = body instanceof Blob;   // a screenshot goes up as-is, everything else as JSON
    if (raw) headers['Content-Type'] = body.type;
    else if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (write) headers['X-XSRF-TOKEN'] = csrfCookie();
    const res = await fetch(`/api/moderator${path}`, { method, headers, body: body === undefined ? undefined : raw ? body : JSON.stringify(body) });
    const data = res.status === 204 ? null : await res.json().catch(() => ({}));
    if (!res.ok) {
        const err = new Error((data && (data.message || data.error)) || `Request failed (${res.status})`);
        err.status = res.status;
        throw err;
    }
    return data;
}

export const login = (identifier, password) => call('/login', { method: 'POST', body: { identifier, password } });
export const logout = () => call('/logout', { method: 'POST' });
/** The signed-in moderator, or null when signed out. */
export const me = () => call('/me').catch((e) => { if (e.status === 401) return null; throw e; });
export const changePassword = (current, password) => call('/password', { method: 'PUT', body: { current, password } });

export const cases = () => call('/investigations');
export const openCase = (id) => call(`/investigations/${id}`);
export const addFinding = (id, text, recommendation) => call(`/investigations/${id}/findings`, { method: 'POST', body: { text, recommendation: recommendation || null } });
export const addScreenshot = (id, findingId, file) => call(`/investigations/${id}/findings/${findingId}/screenshots`, { method: 'POST', body: file });
