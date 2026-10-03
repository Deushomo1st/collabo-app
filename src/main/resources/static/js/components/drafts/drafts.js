// drafts: the "Drafts" dialog on your profile. Lists posts you saved for later; Continue opens the post page on that draft.
// Text goes in through textContent only (h()).
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { draftsOf, draftDelete } from '/js/services/api.js';
import { h, toast } from '/js/services/dom.js';

const ago = (iso) => new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });

export async function openDrafts() {
    const { panel } = await openGlassBlurDialog({ size: 'lg', label: 'Drafts', html: '<h3 class="glass-blur-dialog__title">Drafts</h3><div class="ap-body"></div>' });
    const body = panel.querySelector('.ap-body');
    async function load() {
        try {
            const rows = await draftsOf();
            body.replaceChildren(...(rows.length ? rows.map(row) : [h('p', { class: 'pc-hint', text: 'No drafts. Save one from the post page and it waits for you here.' })]));
        } catch (err) { body.replaceChildren(h('p', { class: 'pc-error', text: err.message })); }
    }
    const row = (d) => h('div', { class: 'ap-card' },
        h('div', { class: 'pc-top' }, h('strong', { text: d.title || 'Untitled idea' }),
            d.media.length > 0 && h('span', { class: 'sp-tag sp-tag--muted', text: `${d.media.length} file${d.media.length === 1 ? '' : 's'}` }),
            h('time', { class: 'pc-time', datetime: d.updatedAt, text: `Saved ${ago(d.updatedAt)}` })),
        d.body && h('p', { class: 'ap-text', text: d.body.length > 160 ? `${d.body.slice(0, 160)}…` : d.body }),
        h('div', { class: 'pc-actions' },
            h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/create-post.html?draft=${d.id}`, text: 'Continue' }),
            h('button', { class: 'pc-btn pc-btn--danger', type: 'button', text: 'Delete', onclick: async () => {
                try { await draftDelete(d.id); load(); } catch (err) { toast(err.message); }
            } })));
    load();
}
