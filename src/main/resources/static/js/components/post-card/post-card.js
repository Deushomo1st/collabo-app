// One post on The Gaze: author, idea, deadline, shout-out, comments, and (for your own) deadline and delete.
// postCard(post, { onGone }) returns an element that keeps itself up to date; onGone() runs after a delete.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { postShout, postUnshout, postDelete, postWindow, commentsOf, commentAdd, commentDelete } from '/js/services/api.js';
import { openApply, openReview } from '/js/components/applications/applications.js';
import { openCollaborators } from '/js/components/collaborators/collaborators.js';
import { h, toast, day, profileHref } from '/js/services/dom.js';
import { face } from '/js/services/face.js';

const MAX_COMMENT = 500;
const when = (iso) => new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
// <input type="datetime-local"> wants local time without a zone.
const localInput = (iso) => { const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };
const person = (u) => h('a', { class: 'pc-who', href: profileHref(u.username) }, face(u.username, 'sp-avatar--sm'), u.username);

const eyeIcon = () => { const i = h('span', { 'aria-hidden': 'true' }); i.innerHTML = '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>'; return i; };   // fixed markup, no user text

export function postCard(initial, { onGone } = {}) {
    let p = initial;
    let open = false;          // comments expanded
    let comments = null;       // loaded on first open
    const root = h('article', { class: 'pc sp-glass' });

    async function act(fn) {
        try { p = await fn(); draw(); } catch (err) { toast(err.message); }
    }

    async function copyLink() {
        try { await navigator.clipboard.writeText(`${location.origin}/HTML-pages/gaze.html?post=${p.id}`); toast('Link copied.'); }
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
        return h('div', { class: 'pc-comments' },
            comments.length === 0 && h('p', { class: 'pc-hint', text: 'No comments yet.' }),
            ...comments.map((c) => h('div', { class: 'pc-comment' },
                h('div', {}, person(c.author), ' ', h('time', { class: 'pc-time', datetime: c.createdAt, text: when(c.createdAt) })),
                h('p', { text: c.body }),
                (c.mine || p.mine) && h('button', { class: 'pc-link', type: 'button', text: 'Delete', onclick: async () => {
                    try { await commentDelete(p.id, c.id); comments = comments.filter((x) => x.id !== c.id); draw(); }
                    catch (err) { toast(err.message); }
                } }))),
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
        root.replaceChildren(...[
            p.shoutedBy && h('p', { class: 'pc-shouted' }, person(p.shoutedBy), ' shouted this out'),
            h('div', { class: 'pc-top' },
                person(p.author),
                p.author.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: p.author.preferredTitle }),
                h('span', { class: `sp-tag ${closed ? 'sp-tag--muted' : 'sp-tag--ok'}`, text: closed ? 'Closed' : formed ? 'Space formed' : 'Pending' }),
                h('time', { class: 'pc-time', datetime: p.createdAt, text: day(p.createdAt) }),
                h('span', { class: 'pc-time pc-views', title: 'Times someone else opened this idea', 'aria-label': `${p.views} views` }, eyeIcon(), String(p.views))),
            h('h3', { class: 'pc-title', text: p.title }),
            h('p', { class: 'pc-body', text: p.body }),
            p.media?.length > 0 && h('div', { class: `pc-media pc-media--${Math.min(p.media.length, 3)}` }, ...p.media.map((m) => m.kind === 'VIDEO'
                ? h('video', { src: `/api/media/${m.id}`, controls: true, preload: 'metadata', playsinline: true })
                : h('img', { src: `/api/media/${m.id}`, alt: 'Attached picture', loading: 'lazy' }))),
            p.hashtags?.length > 0 && h('p', { class: 'pc-tags' }, ...p.hashtags.map((t) => h('span', { text: `#${t}` }))),
            h('p', { class: 'pc-hint', text: formed ? 'A space was formed for this idea.' : p.applyBy ? `${closed ? 'Applications closed' : 'Applications close'} ${when(p.applyBy)}` : 'Open to applications, no deadline' }),
            h('div', { class: 'pc-actions' },
                !p.mine && p.shoutsOn !== false && h('button', { class: `pc-btn ${p.shouted ? 'is-on' : ''}`, type: 'button', 'aria-pressed': String(p.shouted),
                    text: `${p.shouted ? 'Shouted out' : 'Shout out'} · ${p.shouts}`, onclick: () => act(() => (p.shouted ? postUnshout : postShout)(p.id)) }),
                p.mine && p.shouts > 0 && h('span', { class: 'pc-hint', text: `${p.shouts} shout-out${p.shouts === 1 ? '' : 's'}` }),
                h('button', { class: 'pc-btn', type: 'button', 'aria-expanded': String(open), text: open ? 'Hide comments' : 'Comments', onclick: toggleComments }),
                h('button', { class: 'pc-btn', type: 'button', text: 'Copy link', onclick: copyLink }),
                !p.mine && p.applied && p.applied !== 'WITHDRAWN' && h('span', { class: 'sp-tag sp-tag--ok', text: 'Applied' }),
                !p.mine && !closed && !formed && (!p.applied || p.applied === 'WITHDRAWN') && h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Apply',
                    onclick: () => openApply(p, (state) => { p = { ...p, applied: state }; draw(); }) }),
                p.mine && h('button', { class: 'pc-btn', type: 'button', text: `Applicants · ${p.applicants ?? 0}`, onclick: () => openReview(p) }),
                p.mine && h('button', { class: 'pc-btn', type: 'button', text: 'Collaborators', onclick: () => openCollaborators(p) }),
                p.mine && formed && h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/space.html?post=${p.id}`, text: 'Open space' }),
                p.mine && !formed && h('button', { class: 'pc-btn', type: 'button', text: 'Deadline', onclick: editWindow }),
                p.mine && !formed && h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Delete', onclick: remove })),
            open && comments && commentBox()].filter(Boolean));
    }

    draw();
    return root;
}
