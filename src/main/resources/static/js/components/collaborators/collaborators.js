// Collaborators on the page: the founder's "Collaborators" dialog for a post, and "Collaborations" (requests to you, and ones you are in).
// Each opener builds a dialog; text goes in through textContent only (h()).
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { collaboratorsOf, collaboratorInvite, collaboratorAnswer, collaboratorRemove, collaborationsMine, currentUser } from '/js/services/api.js';
import { openReview } from '/js/components/applications/applications.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const dialog = (label, title, size = 'md') => openGlassBlurDialog({ size, label, html: `<h3 class="glass-blur-dialog__title">${title}</h3><div class="ap-body"></div>` });
const stateTag = (s) => h('span', { class: `sp-tag ${s === 'ACTIVE' ? 'sp-tag--ok' : 'sp-tag--muted'}`, text: s === 'ACTIVE' ? 'Collaborator' : 'Asked' });

/** The founder's list of collaborators for one post, with a box to ask a mutual follow. */
export async function openCollaborators(post) {
    const { panel } = await dialog('Collaborators', `Collaborators · ${post.title}`);
    const body = panel.querySelector('.ap-body');
    const list = h('div', { class: 'ap-list', 'aria-live': 'polite' });
    const name = h('input', { class: 'sp-input', maxlength: 40, placeholder: 'Username', 'aria-label': 'Username to ask' });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = h('form', { class: 'ap-tools' }, name, h('button', { class: 'pc-btn pc-btn--brand', type: 'submit', text: 'Ask' }));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await collaboratorInvite(post.id, name.value); name.value = ''; err.hidden = true; load(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
    async function load() {
        try {
            const rows = await collaboratorsOf(post.id);
            list.replaceChildren(...(rows.length ? rows.map((c) => h('div', { class: 'ap-card' },
                h('div', { class: 'pc-top' }, h('a', { class: 'pc-who', href: profileHref(c.person.username), target: '_blank', rel: 'noopener', text: c.person.username }), stateTag(c.state)),
                h('div', { class: 'pc-actions' }, h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: c.state === 'ACTIVE' ? 'Remove' : 'Cancel request', onclick: async () => {
                    try { await collaboratorRemove(post.id, c.person.username); load(); } catch (ex) { toast(ex.message); }
                } })))) : [h('p', { class: 'pc-hint', text: 'No collaborators yet.' })]));
        } catch (ex) { list.replaceChildren(h('p', { class: 'pc-error', text: ex.message })); }
    }
    body.append(h('p', { class: 'pc-hint', text: 'Collaborators review applicants with you and sit in the space. You can ask people you follow who follow you back.' }), form, err, list);
    load();
}

/** Requests to collaborate that you have not answered, and the ideas you already collaborate on. */
export async function openCollaborations() {
    const { panel } = await dialog('Collaborations', 'Collaborations', 'lg');
    return collaborationsInto(panel.querySelector('.ap-body'));
}

/** Fills any element with the collaboration requests and collaborations (the notification panel's Collaborations filter uses this). */
export async function collaborationsInto(body) {
    const me = await currentUser().catch(() => null);
    async function load() {
        try {
            const rows = await collaborationsMine();
            body.replaceChildren(...(rows.length ? rows.map(row) : [h('p', { class: 'pc-hint', text: 'No collaboration requests.' })]));
        } catch (ex) { body.replaceChildren(h('p', { class: 'pc-error', text: ex.message })); }
    }
    const answer = (postId, accept, label) => h('button', { class: `pc-btn ${accept ? 'pc-btn--brand' : ''}`, type: 'button', text: label, onclick: async () => {
        try { await collaboratorAnswer(postId, accept); load(); } catch (ex) { toast(ex.message); }
    } });
    const row = (c) => h('div', { class: 'ap-card' },
        h('div', { class: 'pc-top' }, h('strong', { text: c.postTitle }), stateTag(c.state)),
        h('p', { class: 'pc-hint' }, 'by ', h('a', { class: 'pc-who', href: profileHref(c.founder.username), text: c.founder.username })),
        c.state === 'INVITED'
            ? h('div', { class: 'pc-actions' }, answer(c.postId, true, 'Accept'), answer(c.postId, false, 'Decline'))
            : h('div', { class: 'pc-actions' },
                c.postStatus === 'formed'
                    ? h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/space.html?post=${c.postId}`, text: 'Open space' })
                    : h('button', { class: 'pc-btn pc-btn--brand', type: 'button', text: 'Review applicants', onclick: () => openReview({ id: c.postId, title: c.postTitle, status: c.postStatus }) }),
                me && h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Step down', onclick: async () => {
                    try { await collaboratorRemove(c.postId, me.username); load(); } catch (ex) { toast(ex.message); }
                } })));
    load();
}
