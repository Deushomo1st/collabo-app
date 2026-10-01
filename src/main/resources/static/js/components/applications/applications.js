// Applications on the page: the Apply dialog, the founder's review stack and "My applications".
// Each opener builds a dialog; text goes in through textContent only (h()).
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { applyTo, founderCredentials } from '/js/services/api.js';
import { h, toast, day } from '/js/services/dom.js';

const MAX_WORDS = 150;
const words = (s) => (s.trim() ? s.trim().split(/\s+/).length : 0);
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

/** The founder's review of a post's applicants is a page of its own. */
export const openReview = (post) => { location.href = `/HTML-pages/applicants.html?post=${post.id}`; };

/** Your own applications are a page of their own. */
export const openMine = () => { location.href = '/HTML-pages/applications.html'; };
