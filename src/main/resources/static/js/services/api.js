// API service layer — ALL fetch() calls live here.
// Single source of truth: when auth tokens arrive later, they attach in
// ONE place and every component instantly becomes secure.

export async function registerUser(userData) {
    const response = await fetch('/api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userData)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(data.message || 'Failed to create account. Please try again.');
    }
    return data;
}
