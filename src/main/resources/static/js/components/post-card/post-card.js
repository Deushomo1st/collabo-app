// One post on The Gaze: author, idea, deadline, shout-out, comments, and (for your own) deadline and delete.
// postCard(post, { onGone }) returns an element that keeps itself up to date; onGone() runs after a delete.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { postShout, postUnshout, postDelete, postWindow, commentsOf, commentAdd, commentDelete } from '/js/services/api.js';
import { h, toast, day, profileHref } from '/js/services/dom.js';

const MAX_COMMENT = 500;
const when = (iso) => new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
// <input type="datetime-local"> wants local time without a zone.
const localInput = (iso) => { const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };
const person = (u) => h('a', { class: 'pc-who', href: profileHref(u.username), text: u.username });

export function postCard(initial, { onGone } = {}) {
    let p = initial;
    let open = false;          // comments expanded
    let comments = null;       // loaded on first open
    const root = h('article', { class: 'pc sp-glass' });

    async function act(fn) {
        try { p = await fn(); draw(); } catch (err) { toast(err.message); }
    }

    async function toggleComments() {
        open = !open;
        if (open && !comments) {
            try { comments = await commentsOf(p.id); } catch (err) { open = false; toast(err.message); }
        }
        draw();
    }

    function commentBox() {
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
            form);
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
        root.replaceChildren(...[
            p.shoutedBy && h('p', { class: 'pc-shouted' }, person(p.shoutedBy), ' shouted this out'),
            h('div', { class: 'pc-top' },
                person(p.author),
                p.author.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: p.author.preferredTitle }),
                h('span', { class: `sp-tag ${closed ? 'sp-tag--muted' : 'sp-tag--ok'}`, text: closed ? 'Closed' : 'Pending' }),
                h('time', { class: 'pc-time', datetime: p.createdAt, text: day(p.createdAt) })),
            h('h3', { class: 'pc-title', text: p.title }),
            h('p', { class: 'pc-body', text: p.body }),
            h('p', { class: 'pc-hint', text: p.applyBy ? `${closed ? 'Applications closed' : 'Applications close'} ${when(p.applyBy)}` : 'Open to applications, no deadline' }),
            h('div', { class: 'pc-actions' },
                !p.mine && h('button', { class: `pc-btn ${p.shouted ? 'is-on' : ''}`, type: 'button', 'aria-pressed': String(p.shouted),
                    text: `${p.shouted ? 'Shouted out' : 'Shout out'} · ${p.shouts}`, onclick: () => act(() => (p.shouted ? postUnshout : postShout)(p.id)) }),
                p.mine && p.shouts > 0 && h('span', { class: 'pc-hint', text: `${p.shouts} shout-out${p.shouts === 1 ? '' : 's'}` }),
                h('button', { class: 'pc-btn', type: 'button', 'aria-expanded': String(open), text: open ? 'Hide comments' : 'Comments', onclick: toggleComments }),
                p.mine && h('button', { class: 'pc-btn', type: 'button', text: 'Deadline', onclick: editWindow }),
                p.mine && h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Delete', onclick: remove })),
            open && comments && commentBox()].filter(Boolean));
    }

    draw();
    return root;
}
