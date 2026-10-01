// The Report page: a screenshot or video of what went wrong, and a short write-up. It goes to the admin console.
// Files go up first (POST /api/media, the same as post pictures), then the report names them. Text goes in through textContent only.
import { h, toast } from '/js/services/dom.js';
import { currentUser, mediaUpload, mediaDiscard, mediaUrl, reportSend } from '/js/services/api.js';
import { mountComposeNav } from '/js/services/main-nav.js';
import { reportSet, reportWork } from '/js/services/stash.js';
import { SEND } from '/js/services/icons.js';

const MAX_FILES = 3, MAX_IMAGE = 5 << 20, MAX_VIDEO = 30 << 20, MIN_TEXT = 10;
const $ = (id) => document.getElementById(id);
const GAZE = '/HTML-pages/gaze.html';

const plus = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>';
const cross = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';
const icon = (cls, label, svg, props = {}) => { const b = h('button', { class: cls, type: 'button', 'aria-label': label, ...props }); b.innerHTML = svg; return b; };   // fixed markup, no user text

// Only a path on this site is carried over; the server checks again.
const from = (() => { const f = new URLSearchParams(location.search).get('from'); return f && f.startsWith('/') && !f.startsWith('//') ? f : null; })();

let files = [], uploading = 0, busy = false;
const shots = $('rp-shots'), picker = $('rp-file'), text = $('rp-text'), err = $('rp-error'), send = $('send');
const showError = (t) => { err.textContent = t || ''; err.hidden = !t; };

function drawShots() {
    const tile = (f) => h('div', { class: 'rp-tile' },
        f.kind === 'VIDEO' ? h('video', { src: mediaUrl(f.id), controls: true, preload: 'metadata', playsinline: true }) : h('img', { src: mediaUrl(f.id), alt: 'Your screenshot' }),
        icon('rp-x', 'Remove this file', cross, { onclick: async () => {
            try { await mediaDiscard(f.id); } catch { /* already gone on the server */ }
            files = files.filter((x) => x.id !== f.id); drawShots();
        } }));
    const waiting = Array.from({ length: uploading }, () => h('div', { class: 'rp-tile rp-tile--wait', text: 'Uploading…' }));
    const empty = !files.length && !uploading;
    const add = files.length + uploading < MAX_FILES && icon(`rp-add${empty ? '' : ' rp-add--small'}`, 'Add a screenshot or video', plus, { onclick: () => picker.click() });
    shots.classList.toggle('rp-shots--empty', empty);
    shots.replaceChildren(...files.map(tile), ...waiting, ...[add].filter(Boolean));
}

async function addFiles(list) {
    for (const f of list) {
        if (files.length + uploading >= MAX_FILES) { toast(`At most ${MAX_FILES} files.`); break; }
        const video = f.type.startsWith('video/');
        if (!video && !f.type.startsWith('image/')) { toast(`${f.name} is not a picture or video.`); continue; }
        if (f.size > (video ? MAX_VIDEO : MAX_IMAGE)) { toast(`${f.name} is too big (limit ${video ? 30 : 5} MB).`); continue; }
        uploading++; drawShots();
        try { files = [...files, await mediaUpload(f)]; }
        catch (e) { toast(e.message); }
        finally { uploading--; drawShots(); }
    }
}

async function submit(e) {
    e.preventDefault();
    if (busy || uploading) return showError(uploading ? 'Wait for the upload to finish.' : '');
    if (text.value.trim().length < MIN_TEXT) return showError('Tell us a little more: say what went wrong in a sentence or two.');
    busy = true; send.disabled = true; showError('');
    try {
        await reportSend({ summary: text.value, pageUrl: from, mediaIds: files.map((f) => f.id), anonymous: !!reportSet.read()?.anonymous });
        reportSet.clear(); reportWork.clear();
        $('rp-main').replaceChildren(h('section', { class: 'rp-done sp-glass' },
            h('h2', { text: 'Thank you. We have it.' }),
            h('p', { text: 'Your report is with the admin now. Nothing else for you to do.' }),
            h('button', { class: 'gz-nav gz-nav--brand', type: 'button', text: 'Go back', onclick: goBack })));
        send.hidden = true;
    } catch (ex) { showError(ex.message); busy = false; send.disabled = false; }
}

function goBack() { if (from) location.href = from; else if (history.length > 1) history.back(); else location.href = GAZE; }

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
    send.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${SEND}</svg>`;   // fixed markup
    mountComposeNav('/HTML-pages/report-settings.html', {
        restInner: '<path d="M4 22V4h12l-2 4 2 4H4"/>',
        beforeSettings: () => reportWork.write({ text: text.value, files }),
        beforeLeave: (href) => { reportSet.clear(); reportWork.clear(); location.href = href; } });
    const work = reportWork.take();
    if (work) { text.value = work.text; files = work.files; $('rp-count').textContent = `${text.value.length} / 2000`; } else reportSet.clear();
    $('rp-from').textContent = from ? `From ${from}` : '';
    text.addEventListener('input', () => { $('rp-count').textContent = `${text.value.length} / 2000`; showError(''); });
    picker.addEventListener('change', () => { addFiles([...picker.files]); picker.value = ''; });
    $('rp-form').addEventListener('submit', submit);
    drawShots();
}
boot();
