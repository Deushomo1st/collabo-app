// comment-section: the conversation under a post, on the post's own page. Newest first, with the box to add one on top.
// Each comment has its writer's picture on the left and a heart at the right edge (tap to like it). Under each is "N replies ∨":
// tap it and the thread opens beneath, indented, with the same look and a box to reply. Replies nest as deep as people go,
// each level a little smaller.
//
//   import { mountCommentSection } from '/js/components/comment-section/comment-section.js';
//   mountCommentSection(host, post);   // post: the PostResponse (id, mine, commentsOn)
import { h, toast, profileHref } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { skeletonRows } from '/js/services/skeleton.js';
import { commentsOf, commentAdd, commentDelete, commentLike, commentUnlike } from '/js/services/api.js';

const CSS_HREF = '/js/components/comment-section/comment-section.css';
if (!document.querySelector('link[data-component="comment-section"]')) {
    const link = document.createElement('link');
    link.rel = 'stylesheet'; link.href = CSS_HREF; link.dataset.component = 'comment-section';
    document.head.appendChild(link);
}

const MAX = 500;
const when = (iso) => new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
// Icons are fixed markup written here, never user text.
const HEART = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z"/></svg>';
const BIN = '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/></svg>';
const CHEVRON = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>';

export async function mountCommentSection(host, post) {
    let items = null;               // every comment and reply, oldest first, as the server sends them
    const open = new Set();         // ids of the comments whose thread is open
    const shown = new Map();        // how many replies of each thread are showing (a few at a time)
    const drafts = new Map();       // what is typed in each reply box, so a redraw does not lose it
    let focusId = null;             // the reply box to put the cursor in after a redraw
    const title = h('h2', { class: 'cs__h' });
    const list = h('ul', { class: 'cs__list' });
    const section = h('section', { class: 'cs sp-glass', id: 'comment-section', 'aria-label': 'Comments' }, title, list);
    host.replaceChildren(section);
    list.replaceChildren(...skeletonRows(3).map((r) => h('li', {}, r)));

    const tops = () => items.filter((c) => !c.parentId).reverse();                       // newest first
    const repliesOf = (id) => items.filter((c) => c.parentId === id);                    // oldest first, like a conversation
    const STEP = 3, TOP_STEP = 5;
    let topShown = TOP_STEP;         // how many top-level comments are showing; more come five at a time
    const commentsOff = () => post.commentsOn === false;

    function form() {
        if (commentsOff()) return h('p', { class: 'cs__note', text: 'The author turned comments off for this post.' });
        const input = h('input', { class: 'sp-input', maxlength: MAX, placeholder: 'Add a comment', 'aria-label': 'Add a comment', autocomplete: 'off' });
        const btn = h('button', { class: 'sp-btn sp-btn--brand', type: 'submit', text: 'Post' });
        const f = h('form', { class: 'cs__form' }, input, btn);
        f.addEventListener('submit', async (e) => {
            e.preventDefault();
            const text = input.value.trim();
            if (!text) return;
            btn.disabled = true;
            try { items = [...items, await commentAdd(post.id, text)]; input.value = ''; draw(); }
            catch (err) { toast(err.message); }
            finally { btn.disabled = false; input.focus(); }
        });
        return f;
    }

    /** The heart fills and the count moves at once; the server's answer replaces it, and a failure puts it back. */
    async function toggleLike(c) {
        const now = !c.liked;
        swap({ ...c, liked: now, likes: Math.max(0, c.likes + (now ? 1 : -1)) });
        try { swap(await (now ? commentLike : commentUnlike)(post.id, c.id)); }
        catch (err) { swap(c); toast(err.message); }
    }
    function swap(next) { items = items.map((x) => (x.id === next.id ? { ...x, ...next } : x)); draw(); }

    async function remove(c) {
        try {
            await commentDelete(post.id, c.id);
            const gone = new Set([c.id]);   // everything under it goes too
            for (let grew = true; grew;) { grew = false; for (const x of items) if (!gone.has(x.id) && gone.has(x.parentId)) { gone.add(x.id); grew = true; } }
            items = items.filter((x) => !gone.has(x.id));
            draw();
        } catch (err) { toast(err.message); }
    }

    function toggleThread(c) {
        if (open.has(c.id)) open.delete(c.id); else open.add(c.id);
        draw();
    }

    /** Reply boxes: one per open thread. top = the top-level comment the thread belongs to. */
    function replyForm(top) {
        if (commentsOff()) return null;
        const input = h('input', { class: 'sp-input', maxlength: MAX, placeholder: `Reply to ${top.author.username}`, 'aria-label': `Reply to ${top.author.username}`, autocomplete: 'off', value: drafts.get(top.id) || '' });
        input.addEventListener('input', () => drafts.set(top.id, input.value));
        const btn = h('button', { class: 'sp-btn sp-btn--brand', type: 'submit', text: 'Reply' });
        const f = h('form', { class: 'cs__form cs__form--reply' }, input, btn);
        f.addEventListener('submit', async (e) => {
            e.preventDefault();
            const text = input.value.trim();
            if (!text) return;
            btn.disabled = true;
            try { const made = await commentAdd(post.id, text, top.id); items = [...items, made]; drafts.delete(top.id); shown.set(top.id, Infinity); focusId = top.id; swap({ ...top, replies: (top.replies || 0) + 1 }); }
            catch (err) { toast(err.message); btn.disabled = false; }
        });
        if (focusId === top.id) queueMicrotask(() => { input.focus(); input.setSelectionRange(input.value.length, input.value.length); focusId = null; });
        return f;
    }

    function likeButton(c) {
        const heart = h('button', { class: `cs__like${c.liked ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(!!c.liked), 'aria-label': c.liked ? 'Unlike this comment' : 'Like this comment', onclick: () => toggleLike(c) });
        heart.innerHTML = HEART;
        heart.append(h('span', { class: 'cs__n', text: c.likes > 0 ? String(c.likes) : '' }));
        return heart;
    }

    function head(c) {
        return h('div', { class: 'cs__top' },
            h('a', { class: 'cs__who', href: profileHref(c.author.username), text: c.author.username }),
            h('time', { class: 'cs__time', datetime: c.createdAt, text: when(c.createdAt) }));
    }

    /** One comment at any depth. Its replies nest under it, each level a little smaller (the size is set in the css from --lvl). */
    function node(c, depth) {
        const isOpen = open.has(c.id);
        const n = c.replies || 0;
        const toggle = h('button', { class: `cs__toggle${isOpen ? ' is-open' : ''}`, type: 'button', 'aria-expanded': String(isOpen), onclick: () => toggleThread(c) },
            n > 0 ? `${n} ${n === 1 ? 'reply' : 'replies'}` : (commentsOff() ? '' : 'Reply'));
        const chev = h('span', { class: 'cs__chev', 'aria-hidden': 'true' }); chev.innerHTML = CHEVRON;
        if (n > 0 || !commentsOff()) toggle.append(chev);
        const all = isOpen ? repliesOf(c.id) : [];
        const limit = shown.get(c.id) ?? STEP;
        const left = all.length - limit;
        const thread = isOpen && h('div', { class: 'cs__thread' },
            h('ul', { class: 'cs__replies' }, ...all.slice(0, limit).map((r) => node(r, depth + 1))),
            left > 0 && h('button', { class: 'cs__toggle cs__more', type: 'button', text: `Show ${Math.min(STEP, left)} more ${Math.min(STEP, left) === 1 ? 'reply' : 'replies'}`, onclick: () => { shown.set(c.id, limit + STEP); draw(); } }),
            replyForm(c));
        return h('li', { class: depth ? 'cs__node' : 'cs__item', style: `--lvl:${depth}` },
            h('div', { class: `cs__row${depth ? ' cs__row--reply' : ''}` },
                h('a', { class: 'cs__pic', href: profileHref(c.author.username), 'aria-label': c.author.username }, face(c.author.username)),
                h('div', { class: 'cs__main' }, head(c), h('p', { class: 'cs__body', text: c.body }),
                    h('div', { class: 'cs__acts' },
                        (c.mine || post.mine) && (() => { const b = h('button', { class: 'cs__del', type: 'button', title: 'Delete', 'aria-label': 'Delete comment', onclick: () => remove(c) }); b.innerHTML = BIN; return b; })(),
                        (n > 0 || !commentsOff()) && toggle)),
                likeButton(c)),
            thread);
    }

    function draw() {
        title.replaceChildren('Comments', h('span', { class: 'cs__count', text: String(items.length) }));
        const f = section.querySelector('.cs > .cs__form, .cs > .cs__note') || form();
        if (!f.isConnected) title.after(f);
        const t = tops();
        const more = t.length - topShown;
        list.replaceChildren(...(t.length ? t.slice(0, topShown).map((c) => node(c, 0)) : [h('li', { class: 'cs__empty', text: 'No comments yet. Be the first.' })]),
            ...(more > 0 ? [h('li', { class: 'cs__moretop' }, h('button', { class: 'cs__toggle cs__more', type: 'button', text: `Show ${Math.min(TOP_STEP, more)} more comments`, onclick: () => { topShown += TOP_STEP; draw(); } }))] : []));
    }

    try { items = await commentsOf(post.id); draw(); }
    catch (err) { list.replaceChildren(h('li', { class: 'cs__empty', text: err.message || 'Could not load the comments.' })); }
}
