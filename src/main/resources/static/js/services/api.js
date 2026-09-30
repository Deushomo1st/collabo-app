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

// fetch + JSON + CSRF. Returns { response, data }. The token cookie is fetched before the first write.
async function send(path, { method = 'GET', body } = {}) {
    const write = method !== 'GET';
    if (write && !csrfCookie()) await fetch('/api/auth/csrf');
    const headers = {};
    const raw = body instanceof Blob;   // a picture goes up as-is, everything else as JSON
    if (raw) headers['Content-Type'] = body.type;
    else if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (write) headers['X-XSRF-TOKEN'] = csrfCookie();
    const response = await fetch(path, { method, headers, body: body === undefined ? undefined : raw ? body : JSON.stringify(body) });
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
export const yarnHistory = (id, before) => yarn(`/threads/${id}/yarns${q({ before, limit: 50 })}`);
export const yarnSend = (id, body) => yarn(`/threads/${id}/yarns`, { method: 'POST', body: { body } });
export const yarnMarkRead = (id) => yarn(`/threads/${id}/read`, { method: 'POST' });
export const yarnPrefs = (id, prefs) => yarn(`/threads/${id}/prefs`, { method: 'PATCH', body: prefs });
export const yarnRespond = (id, accept) => yarn(`/threads/${id}/respond`, { method: 'POST', body: { accept } });
export const yarnBlocked = () => yarn('/blocks');
export const yarnBlock = (userId) => yarn(`/blocks/${userId}`, { method: 'PUT' });
export const yarnUnblock = (userId) => yarn(`/blocks/${userId}`, { method: 'DELETE' });

// ---- Profiles, follows and credentials --------------------------------------
const user = (name, path = '') => `/api/users/${encodeURIComponent(name)}${path}`;
export const profileGet = (name) => call(user(name));
export const profileUpdate = (fields) => call('/api/users/me', { method: 'PATCH', body: fields }, 'Could not save your profile.');
export const profileLinks = (links) => call('/api/users/me/links', { method: 'PUT', body: links }, 'Could not save your links.');
export const avatarUrl = (name, version) => user(name, '/avatar') + (version ? `?v=${version}` : '');
export const avatarSave = (jpegBlob) => call('/api/users/me/avatar', { method: 'PUT', body: jpegBlob }, 'Could not save the picture.');
export const avatarRemove = () => call('/api/users/me/avatar', { method: 'DELETE' });
export const follow = (name) => call(user(name, '/follow'), { method: 'PUT' });
export const unfollow = (name) => call(user(name, '/follow'), { method: 'DELETE' });
export const followers = (name) => call(user(name, '/followers'));
export const following = (name) => call(user(name, '/following'));
export const credentialsOf = (name) => call(user(name, '/credentials'));
export const credentialFeature = (id, featured) => call(`/api/credentials/${id}`, { method: 'PATCH', body: { featured } });
export const credentialShip = (id, title, url) => call(`/api/credentials/${id}/shipped`, { method: 'POST', body: { title, url } });
export const credentialUnship = (id, linkId) => call(`/api/credentials/${id}/shipped/${linkId}`, { method: 'DELETE' });

// ---- Posts and The Gaze ------------------------------------------------------
const post = (id, path = '') => `/api/posts/${id}${path}`;
export const gazeFeed = (feed, { pending, before } = {}) => call(`/api/gaze${q({ feed, pending: pending ? 'true' : '', before })}`);
export const userPosts = (name, tab, before) => call(user(name, '/posts') + q({ tab, before }));
export const postCreate = (title, body, applyBy) => call('/api/posts', { method: 'POST', body: { title, body, applyBy } }, 'Could not post your idea.');
export const postDelete = (id) => call(post(id), { method: 'DELETE' });
export const postWindow = (id, applyBy) => call(post(id, '/window'), { method: 'PATCH', body: { applyBy } });
export const postShout = (id) => call(post(id, '/shout'), { method: 'PUT' });
export const postUnshout = (id) => call(post(id, '/shout'), { method: 'DELETE' });
export const commentsOf = (id) => call(post(id, '/comments'));
export const commentAdd = (id, body) => call(post(id, '/comments'), { method: 'POST', body: { body } });
export const commentDelete = (id, commentId) => call(post(id, `/comments/${commentId}`), { method: 'DELETE' });

// applications: apply/withdraw/mine for applicants; stack/decide/founderCredentials for the review side
export const applyTo = (id, statement) => call(post(id, '/applications'), { method: 'POST', body: { statement } }, 'Could not send your application.');
export const applicationWithdraw = (id) => call(`/api/applications/${id}/withdraw`, { method: 'POST' });
export const applicationsMine = () => call('/api/applications/mine');
export const applicationStack = (id, sort, filter) => call(post(id, `/applications?sort=${sort}${filter ? `&filter=${filter}` : ''}`));
export const applicationDecide = (id, decision) => call(`/api/applications/${id}`, { method: 'PATCH', body: { decision } });
export const founderCredentials = (id) => call(post(id, '/founder-credentials'));

// spaces: form from a post, open, join/leave, members
export const spaceForm = (postId, name) => call(post(postId, '/space'), { method: 'POST', body: { name } }, 'Could not form the space.');
export const spaceOfPost = (postId) => call(post(postId, '/space'));
export const spaceById = (id) => call(`/api/spaces/${id}`);
export const spaceJoin = (id) => call(`/api/spaces/${id}/join`, { method: 'POST' });
export const spaceLeave = (id) => call(`/api/spaces/${id}/leave`, { method: 'POST' });
export const spaceMembers = (id) => call(`/api/spaces/${id}/members`);
export const spaceMemberUpdate = (id, username, fields) => call(`/api/spaces/${id}/members/${encodeURIComponent(username)}`, { method: 'PATCH', body: fields });
export const spaceMemberRemove = (id, username) => call(`/api/spaces/${id}/members/${encodeURIComponent(username)}`, { method: 'DELETE' });

// collaborators: the founder asks; the invitee answers; either side ends it
export const collaboratorsOf = (postId) => call(post(postId, '/collaborators'));
export const collaboratorInvite = (postId, username) => call(post(postId, '/collaborators'), { method: 'POST', body: { username } }, 'Could not send the request.');
export const collaboratorAnswer = (postId, accept) => call(post(postId, accept ? '/collaborators/accept' : '/collaborators/decline'), { method: 'POST' });
export const collaboratorRemove = (postId, username) => call(post(postId, `/collaborators/${encodeURIComponent(username)}`), { method: 'DELETE' });
export const collaborationsMine = () => call('/api/collaborations');

// milestones and payment records of a space
const sp = (id, path) => `/api/spaces/${id}${path}`;
export const milestonesOf = (id) => call(sp(id, '/milestones'));
export const milestoneCreate = (id, title) => call(sp(id, '/milestones'), { method: 'POST', body: { title } });
export const milestoneFulfil = (id, mid, note) => call(sp(id, `/milestones/${mid}/fulfil`), { method: 'POST', body: { note } });
export const milestoneDelete = (id, mid) => call(sp(id, `/milestones/${mid}`), { method: 'DELETE' });
export const milestoneOptOut = (id, mid) => call(sp(id, `/milestones/${mid}/opt-out`), { method: 'POST' });
export const paymentsOf = (id) => call(sp(id, '/payments'));
export const paymentClaim = (id, fields) => call(sp(id, '/payments'), { method: 'POST', body: fields });
export const paymentConfirm = (id, pid) => call(sp(id, `/payments/${pid}/confirm`), { method: 'POST' });
export const paymentCancel = (id, pid) => call(sp(id, `/payments/${pid}/cancel`), { method: 'POST' });
