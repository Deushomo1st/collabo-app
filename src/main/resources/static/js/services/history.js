// What you have already seen on the Gaze, kept on this device for each account (Settings > Privacy > History), newest first.
// ponytail: localStorage per device, not synced across devices; a server-side list if it ever needs to follow the account.
const MAX = 300;
const key = (user) => `collabo.history:${user}`;

export function historyRead(user) {
    try { const xs = JSON.parse(localStorage.getItem(key(user))); return Array.isArray(xs) ? xs : []; } catch { return []; }
}
function write(user, xs) {
    try { localStorage.setItem(key(user), JSON.stringify(xs)); } catch { /* storage full or blocked: history just stops growing */ }
}

/** posts: newest first. A post seen again moves to the front. */
export function historyAdd(user, posts) {
    if (!user || !posts.length) return;
    const seen = new Set(posts.map((p) => p.id));
    write(user, [...posts, ...historyRead(user).filter((p) => !seen.has(p.id))].slice(0, MAX));
}
/** The ids of the 5 newest posts you have seen: the Gaze counts what is newer than them (what is in History no longer sits in the feed). */
export const historyTop = (user) => historyRead(user).sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).slice(0, 5).map((p) => p.id);
export const historyDrop = (user, id) => write(user, historyRead(user).filter((p) => p.id !== id));
export const historyClear = (user) => { try { localStorage.removeItem(key(user)); } catch { /* nothing to clear */ } };

/** Words in the title, body or hashtags, or a username (not an anonymous post's author), every word of the query. */
export function historySearch(posts, text) {
    const words = text.toLowerCase().split(/\s+/).filter(Boolean);
    if (!words.length) return posts;
    return posts.filter((p) => {
        const who = p.anonymous && !p.mine ? '' : `${p.author?.username || ''} ${p.author?.fullName || ''}`;
        const hay = `${p.title} ${p.body} ${(p.hashtags || []).join(' ')} ${who}`.toLowerCase();
        return words.every((w) => hay.includes(w.replace(/^[#@]/, '')));
    });
}
