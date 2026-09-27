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
