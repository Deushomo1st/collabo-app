// Applications on the page: the Apply dialog, the founder's review stack and "My applications".
// Each opener builds a dialog; text goes in through textContent only (h()).
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { applyTo, applicationWithdraw, applicationsMine, applicationStack, applicationDecide, founderCredentials, spaceForm } from '/js/services/api.js';
import { h, toast, day, profileHref } from '/js/services/dom.js';

const MAX_WORDS = 150;
const words = (s) => (s.trim() ? s.trim().split(/\s+/).length : 0);
const STATE = { SUBMITTED: 'Submitted', SHORTLISTED: 'Shortlisted', ACCEPTED: 'Accepted', DECLINED: 'Declined', WITHDRAWN: 'Withdrawn' };
const stateTag = (s) => h('span', { class: `sp-tag ${s === 'ACCEPTED' ? 'sp-tag--ok' : s === 'SHORTLISTED' ? 'sp-tag--brand' : 'sp-tag--muted'}`, text: STATE[s] || s });
const dialog = (label, title, size = 'md') => openGlassBlurDialog({ size, label, html: `<h3 class="glass-blur-dialog__title">${title}</h3><div class="ap-body"></div>` });

/** The founder's credentials, as one applicant may see them. */
export async function openFounderCredentials(postId) {
    const { panel } = await dialog("Founder's credentials", "Founder's credentials");
    const body = panel.querySelector('.ap-body');
    try {
        const c = await founderCredentials(postId);
        body.append(...(c.entries.length ? c.entries.map((e) => h('div', { class: 'ap-card' },
            h('strong', { text: e.title }), e.detail && h('p', { class: 'pc-hint', text: e.detail }),
            h('time', { class: 'pc-hint', datetime: e.occurredAt, text: day(e.occurredAt) })))
            : [h('p', { class: 'pc-hint', text: 'No credentials yet.' })]));
    } catch (err) { body.append(h('p', { class: 'pc-error', text: err.message })); }
}

/** Apply to a post. onApplied(state) runs once it is sent. */
export async function openApply(post, onApplied) {
    const { panel, close } = await dialog('Apply', 'Apply to this idea');
    const text = h('textarea', { class: 'sp-input', rows: 6, required: true, 'aria-label': 'Why you', placeholder: 'Why you? What would you bring?' });
    const count = h('span', { class: 'pc-hint', text: `0 / ${MAX_WORDS} words` });
    const err = h('p', { class: 'pc-error', hidden: true });
    text.addEventListener('input', () => { const n = words(text.value); count.textContent = `${n} / ${MAX_WORDS} words`; count.classList.toggle('pc-error', n > MAX_WORDS); });
    const form = h('form', { class: 'sp-form' }, text, count, err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Send application')),
        h('button', { class: 'pc-link', type: 'button', text: "View founder's credentials", onclick: () => openFounderCredentials(post.id) }));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { const a = await applyTo(post.id, text.value); close(); toast('Application sent.'); onApplied?.(a.state); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
    panel.querySelector('.ap-body').append(form);
}

/** The founder's review stack for one of their posts. */
export async function openReview(post) {
    const { panel } = await dialog('Applicants', `Applicants · ${post.title}`, 'lg');
    let sort = 'recent', filter = '';
    const select = (label, opts, onchange) => h('select', { class: 'sp-input ap-select', 'aria-label': label, onchange: (e) => onchange(e.target.value) },
        ...opts.map(([v, t]) => h('option', { value: v, text: t })));
    const list = h('div', { class: 'ap-list', 'aria-live': 'polite' });
    const formBox = h('div', { class: 'ap-form' });
    async function load() {
        try {
            const rows = await applicationStack(post.id, sort, filter);
            drawForm(rows.some((r) => r.state === 'ACCEPTED'));
            list.replaceChildren(...(rows.length ? rows.map(card) : [h('p', { class: 'pc-hint', text: 'No applications here.' })]));
        } catch (err) { list.replaceChildren(h('p', { class: 'pc-error', text: err.message })); }
    }
    // Forming a space needs someone accepted; the name defaults to the post title.
    function drawForm(anyAccepted) {
        if (post.status === 'formed') return formBox.replaceChildren(h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/space.html?post=${post.id}`, text: 'Open space' }));
        if (!anyAccepted) return formBox.replaceChildren(h('p', { class: 'pc-hint', text: 'Accept at least one applicant to form a space.' }));
        const name = h('input', { class: 'sp-input', maxlength: 80, placeholder: post.title, 'aria-label': 'Space name' });
        const err = h('p', { class: 'pc-error', hidden: true });
        const clock = h('input', { class: 'sp-input ap-clock', type: 'number', min: 48, max: 8760, placeholder: '72 h', 'aria-label': 'Response clock in hours' });
        const pleas = h('input', { type: 'checkbox', checked: true });
        const form = h('form', { class: 'ap-form' },
            h('div', { class: 'ap-tools' }, name, clock, h('button', { class: 'pc-btn pc-btn--brand', type: 'submit', text: 'Form space' })),
            h('label', { class: 'gz-filter' }, pleas, ' Allow pleas'));
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            try { const s = await spaceForm(post.id, { name: name.value, responseClockHours: clock.value ? Number(clock.value) : undefined, pleasEnabled: pleas.checked }); location.href = `/HTML-pages/space.html?id=${s.id}`; }
            catch (ex) { err.textContent = ex.message; err.hidden = false; }
        });
        formBox.replaceChildren(form, h('p', { class: 'pc-hint', text: 'Forming closes the post to new applications and locks your accepted applicants in. The response clock is how long a quiet member has to answer before they can be removed (at least 48 hours, 72 if left empty); pleas let others buy them 12 more.' }), err);
    }
    function card(a) {
        const big = h('p', { class: 'ap-text', text: a.statement });
        const decide = (d, label) => h('button', { class: 'pc-btn', type: 'button', text: label, onclick: async () => {
            try { await applicationDecide(a.id, d); load(); } catch (err) { toast(err.message); }
        } });
        return h('div', { class: 'ap-card' },
            h('div', { class: 'pc-top' }, h('a', { class: 'pc-who', href: profileHref(a.applicant.username), target: '_blank', rel: 'noopener', text: a.applicant.username }),
                a.applicant.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: a.applicant.preferredTitle }), stateTag(a.state),
                h('time', { class: 'pc-time', datetime: a.createdAt, text: day(a.createdAt) })),
            big,
            h('div', { class: 'pc-actions' }, decide('ACCEPT', 'Accept'), a.state !== 'SHORTLISTED' && decide('SHORTLIST', 'Shortlist'), decide('DECLINE', 'Decline'),
                h('a', { class: 'pc-link', href: profileHref(a.applicant.username), target: '_blank', rel: 'noopener', text: 'Credentials' })));
    }
    panel.querySelector('.ap-body').append(
        h('div', { class: 'ap-tools' },
            select('Sort', [['recent', 'Newest first'], ['oldest', 'Oldest first']], (v) => { sort = v; load(); }),
            select('Filter', [['', 'All'], ['unreviewed', 'Unreviewed'], ['shortlisted', 'Shortlisted']], (v) => { filter = v; load(); })),
        formBox, list);
    load();
}

/** Your own applications and where each stands. */
export async function openMine() {
    const { panel } = await dialog('My applications', 'My applications', 'lg');
    const body = panel.querySelector('.ap-body');
    async function load() {
        try {
            const rows = await applicationsMine();
            body.replaceChildren(...(rows.length ? rows.map(row) : [h('p', { class: 'pc-hint', text: 'You have not applied to anything yet.' })]));
        } catch (err) { body.replaceChildren(h('p', { class: 'pc-error', text: err.message })); }
    }
    const row = (a) => h('div', { class: 'ap-card' },
        h('div', { class: 'pc-top' }, h('strong', { text: a.postTitle }), stateTag(a.state),
            h('time', { class: 'pc-time', datetime: a.createdAt, text: day(a.createdAt) })),
        h('p', { class: 'pc-hint' }, 'by ', h('a', { class: 'pc-who', href: profileHref(a.postAuthor.username), text: a.postAuthor.username }),
            a.postStatus === 'closed' ? ' · applications closed' : ''),
        a.state === 'ACCEPTED' && a.postStatus === 'formed' && h('div', { class: 'pc-actions' },
            h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/space.html?post=${a.postId}`, text: 'Open space' })),
        h('p', { class: 'ap-text', text: a.statement }),
        (a.state === 'SUBMITTED' || a.state === 'SHORTLISTED') && h('div', { class: 'pc-actions' },
            h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Withdraw', onclick: async () => {
                try { await applicationWithdraw(a.id); load(); } catch (err) { toast(err.message); }
            } })));
    load();
}
