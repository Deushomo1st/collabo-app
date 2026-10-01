// The post page: write an idea, attach pictures and videos, tag it, save a draft or publish. (Sharing a post by yarn lives on the post itself, once it is up.)
// A page of its own. The bar is Gaze, Yarns, Settings (audience, comments, shout-outs, anonymous live on the settings page); hold it to leave.
import { h, toast } from '/js/services/dom.js';
import { openGlassBlurDialog, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { mountComposeNav } from '/js/services/main-nav.js';
import { postSet, postWork, DEFAULT_POST_SET } from '/js/services/stash.js';
import { SEND } from '/js/services/icons.js';
import {
    currentUser, postCreate, mediaUpload, mediaDiscard, mediaUrl, draftGet, draftSave,
} from '/js/services/api.js';

const MAX_FILES = 6, MAX_IMAGE = 5 << 20, MAX_VIDEO = 30 << 20, MAX_TAGS = 10;
const TAG = /^[\p{L}\p{N}_]{2,30}$/u;
const GAZE = '/HTML-pages/gaze.html';
const $ = (id) => document.getElementById(id);

// Icons are fixed strings written here, never user text.
const ico = (paths) => { const s = h('span', { class: 'ps-ico', 'aria-hidden': 'true' }); s.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`; return s; };
const ICON = {
    save: '<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>',
    share: '<path d="M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v14"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
    link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="M5 12l5 5 9-10"/>',
};

let me, draftId = null, files = [], uploading = 0, tags = [], set = { ...DEFAULT_POST_SET }, dirty = false, busy = false;
let savedIds = new Set();   // files that belong to the saved draft: leaving without saving must not delete those

const title = $('ps-title'), body = $('ps-body'), by = $('ps-by'), form = $('post-form'), err = $('ps-error');
const touch = () => { dirty = true; };
const showError = (text) => { err.textContent = text || ''; err.hidden = !text; };

const fields = () => ({
    title: title.value, body: body.value, applyBy: by.value ? new Date(by.value).toISOString() : null,
    hashtags: tags, mediaIds: files.map((f) => f.id),
    commentsOn: set.commentsOn, shoutsOn: set.shoutsOn, applicationsOn: set.applicationsOn, anonymous: set.anonymous, audience: set.audience, audienceWith: set.audienceWith,
});

const AUDIENCE = { EVERYONE: 'Everyone', FOLLOWERS: 'Followers only', COMMUNITY: 'A community', PEOPLE: 'Picked people' };
const drawSummary = () => {
    $('ps-sum').textContent = [AUDIENCE[set.ui.kind] || 'Everyone', set.anonymous && 'anonymous', !set.applicationsOn && 'regular post', !set.commentsOn && 'no comments', !set.shoutsOn && 'no shout-outs'].filter(Boolean).join(' · ');
};

// ---- pictures and videos ---------------------------------------------------------------------------------------------------
const mediaEl = $('ps-media'), picker = $('ps-file');

function drawMedia() {
    const tile = (f) => h('div', { class: 'ps-tile' },
        f.kind === 'VIDEO' ? h('video', { src: mediaUrl(f.id), controls: true, preload: 'metadata', playsinline: true }) : h('img', { src: mediaUrl(f.id), alt: 'Attached picture' }),
        h('button', { class: 'ps-x', type: 'button', 'aria-label': 'Remove this file', onclick: async () => {
            try { await mediaDiscard(f.id); } catch { /* already gone on the server */ }
            files = files.filter((x) => x.id !== f.id); savedIds.delete(f.id); touch(); drawMedia();
        } }, ico('<path d="M6 6l12 12M18 6L6 18"/>')));
    const busyTiles = Array.from({ length: uploading }, () => h('div', { class: 'ps-tile ps-tile--wait', text: 'Uploading…' }));
    const add = files.length + uploading < MAX_FILES && h('button', { class: 'ps-add', type: 'button', onclick: () => picker.click() },
        ico(ICON.plus), h('span', { text: files.length ? 'Add more' : 'Add pictures or video' }));
    mediaEl.replaceChildren(...files.map(tile), ...busyTiles, ...[add].filter(Boolean));
}

async function addFiles(list) {
    for (const f of list) {
        if (files.length + uploading >= MAX_FILES) { toast(`At most ${MAX_FILES} files on a post.`); break; }
        const video = f.type.startsWith('video/');
        if (!video && !f.type.startsWith('image/')) { toast(`${f.name} is not a picture or video.`); continue; }
        if (f.size > (video ? MAX_VIDEO : MAX_IMAGE)) { toast(`${f.name} is too big (up to ${video ? 30 : 5} MB).`); continue; }
        uploading++; drawMedia();
        try { const m = await mediaUpload(f); files.push(m); touch(); }
        catch (ex) { toast(ex.message); }
        finally { uploading--; drawMedia(); }
    }
}
picker.addEventListener('change', () => { addFiles([...picker.files]); picker.value = ''; });

// ---- hashtags, in the form under the pictures ------------------------------------------------------------------------------------------
const tagsEl = $('ps-tags');
const tagInput = h('input', { class: 'ps-tags__in', maxlength: 31, placeholder: 'Add hashtags', 'aria-label': 'Add a hashtag', autocomplete: 'off' });

function addTag(raw) {
    const t = raw.trim().replace(/^#+/, '').toLowerCase();
    tagInput.value = '';
    if (!t || tags.includes(t)) return;
    if (!TAG.test(t)) return toast('Hashtags are 2 to 30 letters, numbers or underscores.');
    if (tags.length >= MAX_TAGS) return toast(`Use at most ${MAX_TAGS} hashtags.`);
    tags.push(t); touch(); drawTags();
}
tagInput.addEventListener('keydown', (e) => {
    if (['Enter', ' ', ','].includes(e.key) && tagInput.value.trim()) { e.preventDefault(); addTag(tagInput.value); }
    else if (e.key === 'Backspace' && !tagInput.value && tags.length) { tags.pop(); touch(); drawTags(); }
    else if (e.key === 'Enter') e.preventDefault();
});
tagInput.addEventListener('blur', () => addTag(tagInput.value));
function drawTags() {
    tagsEl.replaceChildren(h('span', { class: 'ps-tags__hash', 'aria-hidden': 'true', text: '#' }),
        ...tags.map((t) => h('button', { class: 'ps-chip', type: 'button', title: 'Remove', onclick: () => { tags = tags.filter((x) => x !== t); touch(); drawTags(); } }, `#${t}`)),
        tagInput);
}

// ---- the action row: Save draft ----------------------------------------------------------------------------

async function saveDraft(quiet) {
    try {
        const d = await draftSave({ id: draftId, ...fields() });
        draftId = d.id; savedIds = new Set(d.media.map((m) => m.id)); dirty = false;
        if (!quiet) toast('Saved to drafts. Find it on your profile under Drafts.');
        return true;
    } catch (ex) { toast(ex.message); return false; }
}

const actions = () => [
    h('button', { class: 'ps-act', type: 'button', onclick: () => saveDraft(false) }, ico(ICON.save), h('span', { class: 'ps-act__l', text: 'Save draft' }))];

// ---- leaving, saving, publishing ------------------------------------------------------------------------------------------------------
const guard = (e) => { if (dirty) { e.preventDefault(); e.returnValue = ''; } };

/** The text and files wait in this tab while the settings page is open; the page takes them back when you return. */
function toSettings() {
    postSet.write(set);
    postWork.write({ draftId, title: title.value, body: body.value, by: by.value, tags, files, savedIds: [...savedIds], dirty });
    window.removeEventListener('beforeunload', guard);
}

const forget = () => { postSet.clear(); postWork.clear(); };

/** Go somewhere else on the site; unsaved changes get the Save-a-draft question first. */
async function leave(href) {
    const go = () => { forget(); dirty = false; location.href = href; };
    if (!dirty) return go();
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Leave the post page', html:
        '<h3 class="glass-blur-dialog__title">Save this as a draft?</h3><p class="pc-hint">You have changes that are not posted.</p><div class="glass-blur-dialog__actions"></div>' });
    panel.querySelector('.glass-blur-dialog__actions').append(
        h('button', { class: 'glass-blur-dialog__btn', type: 'button', text: 'Keep writing', onclick: close }),
        h('button', { class: 'glass-blur-dialog__btn', type: 'button', text: 'Discard', onclick: () => {
            files.filter((f) => !savedIds.has(f.id)).forEach((f) => mediaDiscard(f.id).catch(() => {}));   // uploads that only this unsaved edit knew about
            close(); go();
        } }),
        h('button', { class: 'glass-blur-dialog__btn', type: 'button', text: 'Save draft', onclick: async () => { if (await saveDraft(true)) { close(); go(); } } }));
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (busy) return;
    if (uploading) return showError('Wait for your files to finish uploading.');
    if (!title.value.trim() || !body.value.trim()) return showError('A post needs a title and a description.');
    showError(''); busy = true; $('publish').disabled = true;
    try {
        const p = await postCreate({ ...fields(), draftId });
        dirty = false; forget();
        try { sessionStorage.setItem('collaboToast', 'Posted.'); } catch { /* the post is up either way */ }
        location.replace(`/HTML-pages/post-view.html?id=${p.id}`);
    } catch (ex) {
        showError(ex.message); busy = false; $('publish').disabled = false;
    }
});

for (const el of [title, body, by]) el.addEventListener('input', touch);
window.addEventListener('beforeunload', guard);

async function boot() {
    me = await currentUser().catch(() => null);
    if (!me) return location.replace('/HTML-pages/index.html');
    $('publish').innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${SEND}</svg>`;   // fixed markup
    mountComposeNav('/HTML-pages/post-settings.html', { beforeLeave: leave, beforeSettings: toSettings, restInner: '<path d="M12 5v14M5 12h14"/>' });
    const id = new URLSearchParams(location.search).get('draft');
    const work = postWork.take();
    if (work) {   // back from the settings page: the text and files are as you left them
        draftId = work.draftId; title.value = work.title; body.value = work.body; by.value = work.by;
        tags = work.tags; files = work.files; savedIds = new Set(work.savedIds);
        set = { ...DEFAULT_POST_SET, ...(postSet.read() || {}) };
    } else if (id) {
        postSet.clear();
        try {
            const d = await draftGet(id);
            draftId = d.id; title.value = d.title || ''; body.value = d.body || '';
            if (d.applyBy && new Date(d.applyBy) > new Date()) { const t = new Date(d.applyBy); t.setMinutes(t.getMinutes() - t.getTimezoneOffset()); by.value = t.toISOString().slice(0, 16); }
            tags = d.hashtags; files = d.media; savedIds = new Set(d.media.map((m) => m.id));
            set.commentsOn = d.commentsOn; set.shoutsOn = d.shoutsOn;
        } catch (ex) { toast(ex.message); }
    } else postSet.clear();
    $('ps-actions').replaceChildren(...actions());
    drawMedia(); drawTags(); drawSummary();
    dirty = work ? work.dirty : false;
    title.focus();
}

preloadGlassBlurDialog();
boot();
