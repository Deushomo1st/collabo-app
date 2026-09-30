// The Gaze: everyone's ideas newest first, or Shared Gaze (your network only). Compose, filter, page.
// All network calls live in js/services/api.js; text goes in through textContent only.
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { openGlassBlurDialog, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { postCard } from '/js/components/post-card/post-card.js';
import { openMine } from '/js/components/applications/applications.js';
import { currentUser, gazeFeed, postCreate } from '/js/services/api.js';
import { h, toast, profileHref } from '/js/services/dom.js';

const MAX_TITLE = 120, MAX_BODY = 2000;
const EMPTY = {
    gaze: 'Nothing here yet. Be the first to post an idea.',
    shared: 'Nothing from your network yet. Follow people, or ask them to follow you.',
};

let feed = 'gaze';
let pendingOnly = false;
let next = null;       // cursor for the next page, null on the last one
let loading = false;

const list = () => document.getElementById('list');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));

function controls() {
    const tab = (id, label) => h('button', { class: 'gz-tab', role: 'tab', type: 'button', 'aria-selected': String(feed === id), text: label,
        onclick: () => { feed = id; drawControls(); load(true); } });
    const pending = h('input', { type: 'checkbox', checked: pendingOnly, onchange: (e) => { pendingOnly = e.target.checked; load(true); } });
    return [h('div', { class: 'gz-tabs', role: 'tablist' }, tab('gaze', 'The Gaze'), tab('shared', 'Shared Gaze')),
        h('label', { class: 'gz-filter' }, pending, ' Open to applications only')];
}
const drawControls = () => document.getElementById('controls').replaceChildren(...controls());

async function load(fresh) {
    if (loading) return;
    loading = true;
    if (fresh) { next = null; list().replaceChildren(h('p', { class: 'gz-empty', text: 'Loading…' })); }
    try {
        const page = await gazeFeed(feed, { pending: pendingOnly, before: fresh ? undefined : next });
        next = page.next || null;
        const cards = page.items.map((p) => { const c = postCard(p, { onGone: () => c.remove() }); return c; });
        if (fresh) list().replaceChildren(...(cards.length ? cards : [h('p', { class: 'gz-empty', text: EMPTY[feed] })]));
        else list().querySelector('.gz-more')?.before(...cards);
    } catch (err) {
        if (err.status === 401) return toLogin();
        list().replaceChildren(h('p', { class: 'gz-empty', text: err.message }), h('button', { class: 'gz-btn', type: 'button', text: 'Try again', onclick: () => load(true) }));
    } finally { loading = false; }
    list().querySelector('.gz-more')?.remove();
    if (next) list().append(h('button', { class: 'gz-btn gz-more', type: 'button', text: 'Show more', onclick: () => load(false) }));
}

async function compose() {
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: 'New idea', html:
        '<h3 class="glass-blur-dialog__title">Post an idea</h3><form class="sp-form"></form>' });
    const title = h('input', { class: 'sp-input', id: 'gz-title', maxlength: MAX_TITLE, required: true, placeholder: 'What are you building?' });
    const body = h('textarea', { class: 'sp-input', id: 'gz-body', rows: 6, maxlength: MAX_BODY, required: true, placeholder: 'Who do you need, and what will you make together?' });
    const by = h('input', { class: 'sp-input', id: 'gz-by', type: 'datetime-local' });
    const err = h('p', { class: 'gz-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(h('label', { for: 'gz-title' }, 'Title', title), h('label', { for: 'gz-body' }, 'Description', body),
        h('label', { for: 'gz-by' }, 'Applications close (optional)', by), err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Post')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await postCreate(title.value, body.value, by.value ? new Date(by.value).toISOString() : null);
            close(); toast('Posted. Your own ideas live on your profile, not in your Gaze.');
        } catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    document.getElementById('me-link').href = profileHref(me.username);
    drawControls();
    load(true);
}

preloadGlassBlurDialog();
mountThemeSwitcher('#theme-slot', { inline: true });
document.getElementById('compose-btn').addEventListener('click', compose);
document.getElementById('mine-btn').addEventListener('click', openMine);
boot();
