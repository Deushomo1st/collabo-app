// One post on The Gaze: author, idea, deadline, and a row of icons (comment, like, shout-out, copy link, share), then the text buttons.
// postCard(post, { onGone, detail }) returns an element that keeps itself up to date; onGone() runs after a delete.
// detail: the post's own page; the title is not a link and the conversation is the comment section under the card. In a feed, tapping the card
// opens that page, and a few of the comments fade past beside the icons.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { postShout, postUnshout, postLike, postUnlike, postShare, postDelete, postWindow, commentsOf, commentAdd, commentDelete } from '/js/services/api.js';
import { openPostShare } from '/js/components/post-share/post-share.js';
import { openApply, openReview } from '/js/components/applications/applications.js';
import { openCollaborators } from '/js/components/collaborators/collaborators.js';
import { h, toast, day, ago, since, profileHref } from '/js/services/dom.js';
import { face } from '/js/services/face.js';
import { watchSeen } from '/js/services/seen.js';

const MAX_COMMENT = 500;
const when = (iso) => new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
// <input type="datetime-local"> wants local time without a zone.
const localInput = (iso) => { const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };
const person = (u) => h('a', { class: 'pc-who', href: profileHref(u.username) }, face(u.username, 'pc-pic'), u.username);
// the post's own page: the full name above, @username beneath
const byline = (u) => h('a', { class: 'pc-who pc-who--named', href: profileHref(u.username) }, face(u.username, 'pc-pic'),
    h('span', { class: 'pc-names' }, u.fullName && h('strong', { class: 'pc-full', text: u.fullName }), h('span', { class: u.fullName ? 'pc-at' : 'pc-full', text: `@${u.username}` })));

const eyeIcon = () => { const i = h('span', { 'aria-hidden': 'true' }); i.innerHTML = '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>'; return i; };   // fixed markup, no user text

// Icons are fixed markup written here, never user text.
const ICON = {
    comment: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    like: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z"/>',
    shout: '<path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M15 9a4 4 0 0 1 0 6M18 6.5a8 8 0 0 1 0 11"/>',
    link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
    share: '<path d="M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v14"/>',
};
const BIN = '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/></svg>';
const binBtn = (onclick) => { const b = h('button', { class: 'pc-btn pc-btn--danger pc-btn--bin', type: 'button', title: 'Delete', 'aria-label': 'Delete post', onclick }); b.innerHTML = BIN; return b; };
const svgIcon = (paths) => { const i = h('span', { class: 'pc-ic__i', 'aria-hidden': 'true' }); i.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`; return i; };
/** A round icon button; count (optional) sits beside it, on (optional) lights it. */
const iconBtn = (paths, { label, title, on, count, pressed, expanded, cls = '', onclick }) => {
    const props = { class: `pc-ic${cls ? ' ' + cls : ''}${on ? ' is-on' : ''}`, type: 'button', 'aria-label': label, title: title || label, onclick };
    if (pressed !== undefined) props['aria-pressed'] = String(pressed);
    if (expanded !== undefined) props['aria-expanded'] = String(expanded);
    return h('button', props, svgIcon(paths), count != null && h('span', { class: 'pc-ic__n', text: String(count) }));
};
/** One shared clock fades the comment previews on every card in view, rather than a timer per card. */
const flashers = new Set();
let flashClock = null;
function runFlashClock() {
    if (flashClock) return;
    flashClock = setInterval(() => {
        if (document.hidden) return;
        for (const f of [...flashers]) {
            if (!f.el.isConnected) { flashers.delete(f); continue; }
            const r = f.el.getBoundingClientRect();
            if (r.bottom > -200 && r.top < innerHeight + 200) f.tick();   // off-screen cards wait
        }
        if (!flashers.size) { clearInterval(flashClock); flashClock = null; }
    }, 4000);
}
const viewHref = (id) => `/HTML-pages/post-view.html?id=${id}`;

export function postCard(initial, { onGone, detail = false } = {}) {
    let p = initial;
    let open = false;          // comments expanded
    let comments = null;       // loaded on first open
    const root = h('article', { class: `pc sp-glass${detail ? ' pc--detail' : ''}` });
    if (!detail && !initial.mine) watchSeen(root, initial.id);   // in a feed, a card that comes into view counts a view (opening the post counts too)

    // The fading preview of what people said (feed only): one comment at a time, in the space beside the icons.
    const flash = h('span', { class: 'pc-flash', 'aria-hidden': 'true' });
    let flashAt = -1;
    const showFlash = () => {
        const s = p.sample || [];
        if (!s.length) return;
        flashAt = s.length > 1 ? (flashAt + 1 + Math.floor(Math.random() * (s.length - 1))) % s.length : 0;   // a random one, never the same twice running
        flash.replaceChildren(h('strong', { text: s[flashAt].username }), ` ${s[flashAt].body}`);
        flash.classList.add('is-on');
    };
    const tickFlash = () => {
        if ((p.sample || []).length < 2) return;   // one comment just stays put
        flash.classList.remove('is-on');
        setTimeout(showFlash, 600);
    };
    if (!detail && p.sample?.length) {
        flashers.add({ el: flash, tick: tickFlash });
        runFlashClock();
        setTimeout(showFlash, 300 + Math.random() * 1500);   // cards do not all start in step
    }

    async function act(fn) {
        try { p = await fn(); draw(); } catch (err) { toast(err.message); }
    }

    /** Shout-out: the button flips and the count moves at once; the server's answer replaces it, and a failure puts it back. */
    async function shout() {
        const before = p, now = !p.shouted;
        p = { ...p, shouted: now, shouts: Math.max(0, p.shouts + (now ? 1 : -1)) };
        draw();
        try { p = await (now ? postShout : postUnshout)(before.id); draw(); }
        catch (err) { p = before; draw(); toast(err.message); }
    }

    /** Like: the heart fills and the count moves at once; the server's answer replaces it, and a failure puts it back. */
    async function like() {
        const before = p, now = !p.liked;
        p = { ...p, liked: now, likes: Math.max(0, (p.likes || 0) + (now ? 1 : -1)) };
        draw();
        try { p = await (now ? postLike : postUnlike)(before.id); draw(); }
        catch (err) { p = before; draw(); toast(err.message); }
    }

    /** Share: the same rail of people the post page uses; Done yarns them this post. */
    function share() {
        openPostShare({
            picked: new Set(),
            notes: { none: 'Tap people to send them this post by yarn.', some: (n) => `${n} picked. Tap Done to send.` },
            onLink: copyLink,
            onDone: async (picked) => {
                if (!picked.size) return;
                try { const r = await postShare(p.id, [...picked]); toast(`Sent to ${r.shared} of ${picked.size} by yarn.`); }
                catch (err) { toast(err.message); }
            },
        });
    }

    async function copyLink() {
        try { await navigator.clipboard.writeText(`${location.origin}${viewHref(p.id)}`); toast('Link copied.'); }
        catch { toast('Could not copy. Copy the address from the link instead.'); }
    }

    async function toggleComments() {
        open = !open;
        if (open && !comments) {
            try { comments = await commentsOf(p.id); } catch (err) { open = false; toast(err.message); }
        }
        draw();
    }

    function commentBox() {
        const on = p.commentsOn !== false;
        const input = h('input', { class: 'sp-input', maxlength: MAX_COMMENT, placeholder: 'Add a comment', 'aria-label': 'Add a comment' });
        const form = h('form', { class: 'pc-cform' }, input, h('button', { class: 'sp-btn sp-btn--brand', type: 'submit', text: 'Post' }));
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (!input.value.trim()) return;
            try { comments = [...comments, await commentAdd(p.id, input.value)]; draw(); }
            catch (err) { toast(err.message); }
        });
        const tops = comments.filter((c) => !c.parentId);   // replies live in the post's own page
        const replies = comments.length - tops.length;
        return h('div', { class: 'pc-comments' },
            tops.length === 0 && h('p', { class: 'pc-hint', text: 'No comments yet.' }),
            ...tops.map((c) => h('div', { class: 'pc-comment' },
                h('div', {}, person(c.author), ' ', h('time', { class: 'pc-time', datetime: c.createdAt, text: ago(c.createdAt) })),
                h('p', { text: c.body }),
                (c.mine || p.mine) && h('button', { class: 'pc-link', type: 'button', text: 'Delete', onclick: async () => {
                    try { await commentDelete(p.id, c.id); comments = comments.filter((x) => x.id !== c.id); draw(); }
                    catch (err) { toast(err.message); }
                } }))),
            replies > 0 && h('a', { class: 'pc-link', href: viewHref(p.id), text: `See ${replies} ${replies === 1 ? 'reply' : 'replies'}` }),
            on ? form : h('p', { class: 'pc-hint', text: 'The author turned comments off for this post.' }));
    }

    async function editWindow() {
        const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Change deadline', html:
            '<h3 class="glass-blur-dialog__title">Application deadline</h3><form class="sp-form"></form>' });
        const input = h('input', { class: 'sp-input', type: 'datetime-local', value: p.applyBy ? localInput(p.applyBy) : '' });
        const err = h('p', { class: 'pc-error', hidden: true });
        const form = panel.querySelector('form');
        form.append(h('label', {}, 'Applications close (leave empty for no deadline)', input),
            h('p', { class: 'pc-hint', text: 'Extending is instant. Shortening takes effect after at least 24 hours, so nobody is closed out mid-draft.' }),
            err, h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Save')));
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            try { p = await postWindow(p.id, input.value ? new Date(input.value).toISOString() : null); close(); draw(); }
            catch (ex) { err.textContent = ex.message; err.hidden = false; }
        });
    }

    async function remove() {
        const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Delete post', html:
            '<h3 class="glass-blur-dialog__title">Delete this post?</h3><p class="pc-hint">Its comments and shout-outs go with it. This cannot be undone.</p><div class="glass-blur-dialog__actions"></div>' });
        panel.querySelector('.glass-blur-dialog__actions').append(
            h('button', { class: 'glass-blur-dialog__btn', type: 'button', text: 'Delete', onclick: async () => {
                try { await postDelete(p.id); close(); onGone?.(); } catch (err) { toast(err.message); close(); }
            } }));
    }

    function draw() {
        const closed = p.status === 'closed';
        const formed = p.status === 'formed';
        const statusTag = h('span', { class: `sp-tag ${closed ? 'sp-tag--muted' : 'sp-tag--ok'}${detail ? ' pc-status' : ''}`, text: closed ? 'Closed' : formed ? 'Space formed' : 'Pending' });
        const apps = p.applicationsOn !== false;   // false = a regular post: no applications, no status, no deadline
        const views = h('span', { class: 'pc-time pc-views', title: 'Times someone else opened this idea', 'aria-label': `${p.views} views` }, eyeIcon(), String(p.views));
        const title = h('h3', { class: 'pc-title' }, detail ? p.title : h('a', { class: 'pc-title__a', href: viewHref(p.id), text: p.title }));
        root.replaceChildren(...[
            p.shoutedBy && h('p', { class: 'pc-shouted' }, person(p.shoutedBy), ' shouted this out'),
            h('div', { class: 'pc-top' },
                p.anonymous && !p.mine ? h('span', { class: 'pc-who', text: 'Anonymous' }) : (detail ? byline : person)(p.author),
                p.anonymous && p.mine && h('span', { class: 'sp-tag sp-tag--muted', text: 'Anonymous to others' }),
                p.author.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: p.author.preferredTitle }),
                apps && !detail && statusTag,
                !detail && h('time', { class: 'pc-time', datetime: p.createdAt, text: day(p.createdAt) }),
                !detail && views),
            detail ? h('div', { class: 'pc-titlerow' }, title, views) : title,   // on the post's own page the views sit beside the title and the age ends the action row
            h('p', { class: 'pc-body', text: p.body }),
            p.media?.length > 0 && h('div', { class: `pc-media pc-media--${Math.min(p.media.length, 3)}` }, ...p.media.map((m) => m.kind === 'VIDEO'
                ? h('video', { src: `/api/media/${m.id}`, controls: true, preload: 'metadata', playsinline: true })
                : h('img', { src: `/api/media/${m.id}`, alt: 'Attached picture', loading: 'lazy', decoding: 'async' }))),
            p.hashtags?.length > 0 && h('p', { class: 'pc-tags' }, ...p.hashtags.map((t) => h('span', { text: `#${t}` }))),
            apps && h('p', { class: 'pc-hint', text: formed ? 'A space was formed for this idea.' : p.applyBy ? `${closed ? 'Applications closed' : 'Applications close'} ${when(p.applyBy)}` : 'Open to applications, no deadline' }),
            h('div', { class: 'pc-actions pc-actions--icons' },
                iconBtn(ICON.like, { cls: 'pc-ic--like', label: p.liked ? 'Liked' : 'Like', title: p.liked ? 'You liked this' : 'Like', on: p.liked, pressed: !!p.liked, count: p.likes || 0, onclick: like }),
                iconBtn(ICON.comment, detail
                    ? { label: 'Comment', count: p.commentCount > 0 ? p.commentCount : undefined, onclick: () => { const box = document.querySelector('#comment-section input'); box?.scrollIntoView({ block: 'center', behavior: 'smooth' }); box?.focus({ preventScroll: true }); } }
                    : { label: open ? 'Hide comments' : 'Comments', expanded: open, count: p.commentCount > 0 ? p.commentCount : undefined, onclick: toggleComments }),
                !p.mine && p.shoutsOn !== false && iconBtn(ICON.shout, { label: p.shouted ? 'Shouted out' : 'Shout out', title: 'Shout this out to your followers; it lands in your Shout-outs',
                    on: p.shouted, pressed: !!p.shouted, count: p.shouts, onclick: shout }),
                p.mine && p.shouts > 0 && h('span', { class: 'pc-ic pc-ic--static', title: `${p.shouts} shout-out${p.shouts === 1 ? '' : 's'}` }, svgIcon(ICON.shout), h('span', { class: 'pc-ic__n', text: String(p.shouts) })),
                iconBtn(ICON.link, { label: 'Copy link', onclick: copyLink }),
                iconBtn(ICON.share, { label: 'Share by yarn', onclick: share }),
                detail && p.mine && (!apps || !formed) && binBtn(remove),
                !detail && p.sample?.length > 0 && flash,
                detail && h('time', { class: 'pc-time', datetime: p.createdAt, title: day(p.createdAt), text: since(p.createdAt) })),
            apps && h('div', { class: 'pc-actions' },
                !p.mine && p.applied && p.applied !== 'WITHDRAWN' && h('span', { class: 'sp-tag sp-tag--ok', text: 'Applied' }),
                !p.mine && !closed && !formed && (!p.applied || p.applied === 'WITHDRAWN') && h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Apply',
                    onclick: () => openApply(p, (state) => { p = { ...p, applied: state }; draw(); }) }),
                p.mine && h('button', { class: 'pc-btn', type: 'button', text: `Applicants · ${p.applicants ?? 0}`, onclick: () => openReview(p) }),
                p.mine && h('button', { class: 'pc-btn', type: 'button', text: 'Collaborators', onclick: () => openCollaborators(p) }),
                p.mine && formed && h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/space.html?post=${p.id}`, text: 'Open space' }),
                p.mine && !formed && h('button', { class: 'pc-btn', type: 'button', text: 'Deadline', onclick: editWindow }),
                !detail && p.mine && !formed && binBtn(remove),
                detail && statusTag),
            !apps && !detail && p.mine && h('div', { class: 'pc-actions' }, binBtn(remove)),
            !detail && open && comments && commentBox()].filter(Boolean));
    }

    draw();
    if (!detail) root.addEventListener('click', (e) => {   // in a feed, tapping the card (not a control inside it) opens the post's page
        if (e.target.closest('a, button, input, textarea, video, form, label, .pc-comments')) return;
        if (getSelection()?.toString()) return;   // someone is selecting text to copy
        location.href = viewHref(p.id);
    });
    return root;
}
