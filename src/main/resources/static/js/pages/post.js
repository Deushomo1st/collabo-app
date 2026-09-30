// The post page: write an idea, attach pictures and videos, tag it, pick who gets it by yarn, save a draft or publish.
// A page of its own. There are no nav bars here; the only way out is the back button.
import { h, toast } from '/js/services/dom.js';
import { openGlassBlurDialog, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import {
    currentUser, postCreate, mediaUpload, mediaDiscard, mediaUrl, draftGet, draftSave, yarnThreads, yarnDirectory, following, avatarUrl,
} from '/js/services/api.js';

const MAX_FILES = 6, MAX_IMAGE = 5 << 20, MAX_VIDEO = 30 << 20, MAX_TAGS = 10;
const TAG = /^[\p{L}\p{N}_]{2,30}$/u;
const GAZE = '/HTML-pages/gaze.html';
const $ = (id) => document.getElementById(id);

// Icons are fixed strings written here, never user text.
const ico = (paths) => { const s = h('span', { class: 'ps-ico', 'aria-hidden': 'true' }); s.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`; return s; };
const ICON = {
    comment: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    shout: '<path d="M3 11v2a1 1 0 0 0 1 1h3l5 4V6L7 10H4a1 1 0 0 0-1 1z"/><path d="M16 9a4 4 0 0 1 0 6M19 6a8 8 0 0 1 0 12"/>',
    save: '<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>',
    share: '<path d="M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v14"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
    link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="M5 12l5 5 9-10"/>',
};

let me, draftId = null, files = [], uploading = 0, tags = [], commentsOn = true, shoutsOn = true, dirty = false, busy = false;
let savedIds = new Set();   // files that belong to the saved draft: leaving without saving must not delete those
const picked = new Set();   // usernames this post goes to, by yarn, once it is posted

const title = $('ps-title'), body = $('ps-body'), by = $('ps-by'), form = $('post-form'), err = $('ps-error');
const touch = () => { dirty = true; };
const showError = (text) => { err.textContent = text || ''; err.hidden = !text; };

const fields = () => ({
    title: title.value, body: body.value, applyBy: by.value ? new Date(by.value).toISOString() : null,
    hashtags: tags, mediaIds: files.map((f) => f.id), commentsOn, shoutsOn, shareWith: [...picked],
});

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

// ---- hashtags, pinned above the action row ------------------------------------------------------------------------------------------
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

// ---- the action row: Comment · Shoutout · Save · Share ----------------------------------------------------------------------------
function toggle(icon, label, get, set) {
    const state = h('span', { class: 'ps-act__state' });
    const b = h('button', { class: 'ps-act', type: 'button' }, ico(icon), h('span', { class: 'ps-act__l', text: label }), state);
    const draw = () => { b.classList.toggle('is-off', !get()); b.setAttribute('aria-pressed', String(get())); state.textContent = get() ? 'On' : 'Off'; };
    b.addEventListener('click', () => { set(!get()); touch(); draw(); });
    draw();
    return b;
}

const shareBadge = h('span', { class: 'ps-badge', hidden: true });
const shareBtn = h('button', { class: 'ps-act ps-act--share', type: 'button', 'aria-haspopup': 'dialog', onclick: () => openShare() },
    ico(ICON.share), h('span', { class: 'ps-act__l', text: 'Share' }), shareBadge);
const drawShare = () => { shareBadge.textContent = String(picked.size); shareBadge.hidden = picked.size === 0; };

async function saveDraft(quiet) {
    try {
        const d = await draftSave({ id: draftId, ...fields() });
        draftId = d.id; savedIds = new Set(d.media.map((m) => m.id)); dirty = false;
        if (!quiet) toast('Saved to drafts. Find it on your profile under Drafts.');
        return true;
    } catch (ex) { toast(ex.message); return false; }
}

const actions = () => [
    toggle(ICON.comment, 'Comment', () => commentsOn, (v) => { commentsOn = v; }),
    toggle(ICON.shout, 'Shoutout', () => shoutsOn, (v) => { shoutsOn = v; }),
    h('button', { class: 'ps-act', type: 'button', onclick: () => saveDraft(false) }, ico(ICON.save), h('span', { class: 'ps-act__l', text: 'Save' })),
    shareBtn];

// ---- share: a tall nav of the people you talk to; tapping one ticks them for a yarn once the post is up -----------------------------------------
let people = null;
const hue = (name) => [...name].reduce((a, c) => (a * 31 + c.charCodeAt(0)) % 360, 7);

function face(p) {
    const el = h('span', { class: 'sp-avatar ps-face', style: `--hue:${hue(p.username)}`, text: p.username[0].toUpperCase() });
    const img = h('img', { src: avatarUrl(p.username, p.avatar), alt: '', loading: 'lazy' });
    img.addEventListener('error', () => img.remove());
    el.append(img);
    return el;
}

async function loadPeople() {
    if (people) return people;
    const [threads, follows] = await Promise.all([yarnThreads('inbox', 'MYSPACE').catch(() => []), following(me.username).catch(() => [])]);
    const seen = new Map();
    for (const t of threads) {
        if (t.status === 'DECLINED') continue;
        for (const m of t.members) if (m.username !== me.username && !seen.has(m.username)) seen.set(m.username, { username: m.username, avatar: m.avatar });
    }
    for (const f of Array.isArray(follows) ? follows : []) if (!seen.has(f.username)) seen.set(f.username, { username: f.username, avatar: null });
    return (people = [...seen.values()]);
}

async function openShare() {
    const opener = document.activeElement;
    const list = h('div', { class: 'ps-people' });
    const note = h('p', { class: 'ps-share__note' });
    const search = h('input', { class: 'ps-search__in', placeholder: 'Search people', 'aria-label': 'Search people', autocomplete: 'off' });
    const searchBtn = h('button', { class: 'ps-circle', type: 'button', 'aria-label': 'Search people' }, ico(ICON.search));
    const searchBox = h('div', { class: 'ps-search' }, searchBtn, search);
    const linkBtn = h('button', { class: 'ps-circle', type: 'button', 'aria-label': 'Copy link', onclick: () => toast('The link exists once the post is up. Copy it from the post itself.') }, ico(ICON.link));
    const done = h('button', { class: 'gz-nav gz-nav--brand', type: 'button', text: 'Done' });
    const rail = h('div', { class: 'ps-rail' }, h('div', { class: 'ps-rail__head' }, searchBox, linkBtn), list);
    const overlay = h('div', { class: 'ps-share', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'Share with' }, rail, h('div', { class: 'ps-share__foot' }, note, done));
    let extra = [];   // people found by search who you have not talked to yet

    const close = () => {
        overlay.classList.remove('is-on');
        setTimeout(() => { overlay.remove(); document.removeEventListener('keydown', onKey); opener?.focus?.(); }, 220);
        drawShare();
    };
    const onKey = (e) => { if (e.key === 'Escape') close(); };
    const draw = () => {
        const q = search.value.trim().toLowerCase();
        const all = [...(people || []), ...extra.filter((x) => !(people || []).some((p) => p.username === x.username))];
        const shown = all.filter((p) => !q || p.username.toLowerCase().includes(q));
        list.replaceChildren(...(shown.length ? shown.map((p) => h('button', {
            class: `ps-person${picked.has(p.username) ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(picked.has(p.username)), title: p.username,
            onclick: () => {
                if (picked.has(p.username)) picked.delete(p.username); else picked.add(p.username);
                if (!(people || []).some((x) => x.username === p.username)) people.push(p);
                touch(); draw();
            } }, face(p), h('span', { class: 'ps-person__n', text: p.username }), h('span', { class: 'ps-person__tick' }, ico(ICON.check))))
            : [h('p', { class: 'ps-share__empty', text: q ? 'No one by that name yet.' : 'No yarnmates yet. Search for someone.' })]));
        note.textContent = picked.size ? `${picked.size} picked. They each get this post as a yarn once you publish.` : 'Tap people to send them this post by yarn once it is published.';
    };

    let timer;
    search.addEventListener('input', () => {
        draw(); clearTimeout(timer);
        const q = search.value.trim();
        if (q.length < 2) return;
        timer = setTimeout(async () => { try { extra = await yarnDirectory(q); draw(); } catch { /* the local list still works */ } }, 250);
    });
    searchBtn.addEventListener('click', () => { rail.classList.add('is-wide'); searchBox.classList.add('is-open'); search.focus(); });
    done.addEventListener('click', close);
    overlay.addEventListener('pointerdown', (e) => { if (e.target === overlay) close(); });
    document.addEventListener('keydown', onKey);
    document.body.append(overlay);
    requestAnimationFrame(() => overlay.classList.add('is-on'));
    done.focus();
    note.textContent = 'Loading…';
    await loadPeople();
    draw();
}

// ---- leaving, saving, publishing ------------------------------------------------------------------------------------------------------
function goBack() {
    dirty = false;
    const same = document.referrer && new URL(document.referrer).origin === location.origin;
    if (same && history.length > 1) history.back(); else location.href = GAZE;
}

async function leave() {
    if (!dirty) return goBack();
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Leave the post page', html:
        '<h3 class="glass-blur-dialog__title">Save this as a draft?</h3><p class="pc-hint">You have changes that are not posted.</p><div class="glass-blur-dialog__actions"></div>' });
    panel.querySelector('.glass-blur-dialog__actions').append(
        h('button', { class: 'glass-blur-dialog__btn', type: 'button', text: 'Keep writing', onclick: close }),
        h('button', { class: 'glass-blur-dialog__btn', type: 'button', text: 'Discard', onclick: () => {
            files.filter((f) => !savedIds.has(f.id)).forEach((f) => mediaDiscard(f.id).catch(() => {}));   // uploads that only this unsaved edit knew about
            close(); goBack();
        } }),
        h('button', { class: 'glass-blur-dialog__btn', type: 'button', text: 'Save draft', onclick: async () => { if (await saveDraft(true)) { close(); goBack(); } } }));
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (busy) return;
    if (uploading) return showError('Wait for your files to finish uploading.');
    if (!title.value.trim() || !body.value.trim()) return showError('A post needs a title and a description.');
    showError(''); busy = true; $('publish').disabled = true;
    try {
        const p = await postCreate({ ...fields(), draftId });
        dirty = false;
        const sent = picked.size ? ` Sent to ${p.shared} of ${picked.size} by yarn.` : '';
        try { sessionStorage.setItem('collaboToast', `Posted.${sent}`); } catch { /* the post is up either way */ }
        location.replace(`${GAZE}?post=${p.id}`);
    } catch (ex) {
        showError(ex.message); busy = false; $('publish').disabled = false;
    }
});

$('back').addEventListener('click', leave);
for (const el of [title, body, by]) el.addEventListener('input', touch);
window.addEventListener('beforeunload', (e) => { if (dirty) { e.preventDefault(); e.returnValue = ''; } });

async function boot() {
    me = await currentUser().catch(() => null);
    if (!me) return location.replace('/HTML-pages/index.html');
    const id = new URLSearchParams(location.search).get('draft');
    if (id) {
        try {
            const d = await draftGet(id);
            draftId = d.id; title.value = d.title || ''; body.value = d.body || '';
            if (d.applyBy && new Date(d.applyBy) > new Date()) { const t = new Date(d.applyBy); t.setMinutes(t.getMinutes() - t.getTimezoneOffset()); by.value = t.toISOString().slice(0, 16); }
            tags = d.hashtags; files = d.media; savedIds = new Set(d.media.map((m) => m.id));
            commentsOn = d.commentsOn; shoutsOn = d.shoutsOn;
            d.shareWith.forEach((n) => picked.add(n));
        } catch (ex) { toast(ex.message); }
    }
    $('ps-actions').replaceChildren(...actions());   // built after the draft loads, so the On/Off look matches it
    drawMedia(); drawTags(); drawShare();
    dirty = false;
    title.focus();
}

preloadGlassBlurDialog();
boot();
