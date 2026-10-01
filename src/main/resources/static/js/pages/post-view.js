// A post on a page of its own, with its comments open (like opening a tweet). Reached as post-view.html?id=<post id>:
// from a card in the feed, from a copied link, or from a post yarned to you.
import '/js/services/live.js';
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { currentUser, postGet } from '/js/services/api.js';
import { postCard } from '/js/components/post-card/post-card.js';
import { mountCommentSection } from '/js/components/comment-section/comment-section.js';
import { skeletonCards } from '/js/services/skeleton.js';
import { h, toast } from '/js/services/dom.js';

const GAZE = '/HTML-pages/gaze.html';
const $post = document.getElementById('post');
const id = new URLSearchParams(location.search).get('id');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
const say = (text) => $post.replaceChildren(h('p', { class: 'gz-empty', text }));

// Back goes where you came from when that was this app; otherwise to the Gaze.
document.getElementById('back').addEventListener('click', (e) => {
    if (history.length > 1 && document.referrer.startsWith(location.origin)) { e.preventDefault(); history.back(); }
});

(async () => {
    if (!id) return say('That post is gone.');
    $post.replaceChildren(...skeletonCards(1));
    try {
        const me = await currentUser();
        if (!me) return toLogin();
        mountMainNav(me.username, 'Gaze', { onGaze: () => { location.href = GAZE; } });   // a post belongs to the Gaze; tapping it goes back to the feed
        const p = await postGet(id);
        document.title = `${p.title} — COLLABO`;
        $post.replaceChildren(postCard(p, { detail: true, onGone: () => location.replace(GAZE) }));
        mountCommentSection(document.getElementById('comments'), p);
        try { const msg = sessionStorage.getItem('collaboToast'); if (msg) { sessionStorage.removeItem('collaboToast'); toast(msg); } } catch { /* just no message */ }
    } catch (err) {
        if (err.status === 401) return toLogin();
        say(err.status === 404 ? 'That post is gone.' : (err.message || 'Could not load the post.'));
    }
})();
mountThemeSwitcher('#theme-slot', { inline: true, collapse: true });
