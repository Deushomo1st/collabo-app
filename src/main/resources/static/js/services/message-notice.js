// The app speaks, anywhere on the site: new yarns and new notifications come out of the menu button as a bubble with the person's picture,
// and the menu's tiles carry the counts (Yarns, Gaze, Notifications).
import { live } from '/js/services/live.js';
import { yarnThreads, gazeNewer, notificationsList } from '/js/services/api.js';
import { speak } from '/js/services/bubble.js';
import { historyTop } from '/js/services/history.js';
import { whoIn } from '/js/services/notice-who.js';

// New posts on the Gaze since you last looked: asked against the top of the feed this tab remembers (gaze.js keeps it). 0 when it remembers nothing.
export async function newPosts(username) {
    try {
        const top = [...(JSON.parse(sessionStorage.getItem(`gaze:${username}:gaze:false`))?.fresh || []).slice(0, 5).map((p) => p.id), ...historyTop(username)];
        return top.length ? (await gazeNewer('gaze', { pending: false, top })).count : 0;
    } catch { return 0; }
}

export const unreadNotes = async () => (await notificationsList()).filter((n) => !n.read).length;

// Which alert the menu button's red badge shows: the kind that spoke last, if it is still unread; else notifications, else yarns; null when nothing is unread.
let last = null;
export async function alertKind() {
    const [y, n] = await Promise.all([unreadYarns().catch(() => 0), unreadNotes().catch(() => 0)]);
    return last === 'yarn' && y ? 'yarn' : last === 'note' && n ? 'note' : n ? 'note' : y ? 'yarn' : null;
}
const changed = (kind) => { if (kind) last = kind; document.dispatchEvent(new Event('alert-change')); };

// What is worth a bubble: not a post (that is the Latest pill's news) and not new messages (the yarn bubble speaks for them).
const QUIET = [/ posted$/, /^You've got new messages$/];
let known = null;   // ids of the notifications already looked at; null until the first look (that one stays quiet)

async function listen() {
    const all = await notificationsList().catch(() => null);
    if (!all) return;
    const was = known; known = new Set(all.map((n) => n.id));
    if (!was || location.pathname.endsWith('/notifications.html')) return;
    const fresh = all.filter((n) => !n.read && !was.has(n.id) && !QUIET.some((r) => r.test(n.title)));
    changed(fresh.length ? 'note' : null);
    fresh.slice(0, 3).reverse().forEach((n) => {
        const who = whoIn(n);
        speak((who.length ? n.body : n.body ? `${n.title}: ${n.body}` : n.title).slice(0, 90), who[0], who[1], n.link || '/HTML-pages/notifications.html');
    });
}

let seen = null, timer;   // thread -> unread count at the last look; null until the first look (that one stays quiet)
export const unreadYarns = async () => (await yarnThreads('inbox')).filter((t) => !t.muted).reduce((n, t) => n + t.unread, 0);

async function look() {
    const threads = await yarnThreads('inbox').catch(() => null);
    if (!threads) return;
    const was = seen; seen = new Map(threads.map((t) => [t.id, t.unread]));
    if (!was || location.pathname.endsWith('/yarnspaces.html')) return;   // the Yarns page shows them itself
    const news = threads.filter((t) => !t.muted && t.unread > (was.get(t.id) ?? 0));
    changed(news.length ? 'yarn' : null);
    for (const t of news) {
        const from = t.members.find((m) => m.username === t.lastSender) || t.members.find((m) => m.id === t.otherUserId);
        speak(`${t.lastSender}: ${t.lastBody || 'sent a yarn'}`.slice(0, 80), from?.username, undefined, `/HTML-pages/yarnspaces.html#t/${t.id}`);
    }
}

export function watchNotices() {
    look(); listen();
    let t2;
    live.on('yarn', () => { clearTimeout(timer); timer = setTimeout(look, 400); });   // a burst of signals becomes one look
    live.on('notification', () => { clearTimeout(t2); t2 = setTimeout(listen, 400); });
}
