import '/js/services/warm.js';   // the app's pages load in the background, so the first move in is quick
// First-run welcome: welcome, photo, about you, links, pick a path. Every step saves as you leave it, so skipping midway loses nothing.
// New accounts land here once (login sends them; the Gaze sends anyone who slipped past). Network calls live in js/services/api.js.
import { createAvatarCard, openAvatarUpload, preloadAvatar } from '/js/components/avatar/avatar.js';
import { currentUser, profileGet, profileUpdate, profileLinks, avatarUrl, avatarSave, avatarRemove, markWelcomed } from '/js/services/api.js';
import { h } from '/js/services/dom.js';

const $ = (id) => document.getElementById(id);
const GAZE = '/HTML-pages/gaze.html', POST = '/HTML-pages/create-post.html';
const MAX_LINKS = 3;

let me, profile, idx = 0, steps = [], current = null, finishing = false;

const showError = (text) => { $('wl-error').textContent = text || ''; $('wl-error').hidden = !text; };

// ---- the steps: each returns { node, save? } ----------------------------------------------------------------------------------
function welcome() {
    const point = (n, text) => h('li', {}, h('b', { text: n }), text);
    return { node: h('div', {}, h('h1', { text: `Welcome, ${me.username}.` }),
        h('p', { text: "Your people are out there. Let's get you ready to meet them. It takes a minute, and you can skip any step." }),
        h('ul', { class: 'wl-points' },
            point('1', 'Post an idea to The Gaze, and pick your team yourself.'),
            point('2', "Or apply to join someone else's idea with a few honest sentences."),
            point('3', 'Then build together in your Yarnspaces.'))) };
}

function photo() {
    const box = h('div', { class: 'wl-photo' });
    const draw = () => {
        const src = profile.avatarVersion ? avatarUrl(me.username, profile.avatarVersion) : null;
        box.replaceChildren(
            createAvatarCard({ name: me.username, showName: false, editable: true, src,
                onClick: () => openAvatarUpload({ current: src,
                    onSave: async (_url, blob) => {
                        try { profile = blob ? await avatarSave(blob) : await avatarRemove(); showError(''); draw(); }
                        catch (e) { showError(e.message); }
                    } }) }),
            h('small', { text: profile.avatarVersion ? 'Looking good. Tap to change it.' : 'Tap the circle to add a photo.' }));
    };
    draw();
    return { node: h('div', {}, h('h1', { text: 'Put a face to the name' }),
        h('p', { text: 'People pick teammates they can picture. Drag and zoom inside the circle to frame it.' }), box) };
}

function about() {
    const title = h('input', { class: 'sp-input', id: 'wl-title', maxlength: 40, value: profile.preferredTitle || '', placeholder: 'e.g. Backend developer, Illustrator' });
    const bio = h('textarea', { class: 'sp-input', id: 'wl-bio', maxlength: 600, placeholder: 'What do you build, and what are you looking for?' }, profile.bio || '');
    const count = h('span', { class: 'wl-count' });
    const tick = () => { count.textContent = `${bio.value.length} / 600`; };
    bio.addEventListener('input', tick); tick();
    return { node: h('div', {}, h('h1', { text: 'What do you do?' }), h('p', { text: 'A title and a short bio are the first things people read.' }),
            h('label', { for: 'wl-title' }, 'Your title', title), h('label', { for: 'wl-bio' }, 'About you', bio), count),
        save: async () => { profile = await profileUpdate({ preferredTitle: title.value, bio: bio.value }); } };
}

function links() {
    const rows = [...profile.links, ...Array(MAX_LINKS)].slice(0, MAX_LINKS).map((l) => ({
        t: h('input', { class: 'sp-input', maxlength: 60, value: l?.title || '', placeholder: 'Portfolio', 'aria-label': 'Link title' }),
        u: h('input', { class: 'sp-input', maxlength: 500, value: l?.url || '', placeholder: 'https://', 'aria-label': 'Link address' }) }));
    return { node: h('div', {}, h('h1', { text: 'Show your work' }), h('p', { text: 'A portfolio, repo or profile that proves what you have made. Optional.' }),
            h('div', { class: 'wl-links' }, ...rows.map((r) => h('div', { class: 'wl-link-row' }, r.t, r.u)))),
        save: async () => {
            const filled = rows.map((r) => ({ title: r.t.value, url: r.u.value, note: '' })).filter((l) => l.title.trim() || l.url.trim());
            if (filled.length || profile.links.length) profile = await profileLinks(filled);
        } };
}

function path() {
    const choice = (title, text, to) => h('button', { class: 'wl-path', type: 'button', onclick: () => finish(to) }, h('strong', { text: title }), h('span', { text }));
    return { node: h('div', {}, h('h1', { text: 'Where to first?' }), h('p', { text: 'You can do both, any time.' }),
        h('div', { class: 'wl-paths' }, choice('I have an idea', 'Post it and start choosing your team.', POST),
            choice('I want to join a team', 'Browse The Gaze and apply to ideas you believe in.', GAZE))) };
}

// ---- the stage ---------------------------------------------------------------------------------------------------------------
function show(i, back = false) {
    idx = i;
    current = steps[i]();
    current.node.classList.add('wl-step');
    if (back) current.node.classList.add('is-back');
    $('wl-stage').replaceChildren(current.node);
    $('wl-fill').style.width = `${((i + 1) / steps.length) * 100}%`;
    $('wl-back').hidden = i === 0;
    $('wl-next').hidden = i === steps.length - 1;
    $('wl-next').textContent = i === 0 ? "Let's go" : 'Next';
    showError('');
    current.node.querySelector('input, textarea')?.focus({ preventScroll: true });
}

async function go(delta) {
    if (delta > 0 && current?.save) {
        $('wl-next').disabled = true;
        try { await current.save(); }
        catch (e) { showError(e.message); $('wl-next').disabled = false; return; }
        $('wl-next').disabled = false;
    }
    show(idx + delta, delta < 0);
}

async function finish(to = GAZE) {
    if (finishing) return;
    finishing = true;
    try { await markWelcomed(); location.replace(to); }
    catch (e) { finishing = false; showError(e.message); }
}

async function boot() {
    me = await currentUser().catch(() => null);
    if (!me) return location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
    if (!me.needsWelcome) return location.replace(GAZE);   // already done: never again
    profile = await profileGet(me.username);
    steps = [welcome, photo, about, links, path];
    $('wl-next').addEventListener('click', () => go(1));
    $('wl-back').addEventListener('click', () => go(-1));
    $('wl-skip').addEventListener('click', () => finish(GAZE));
    show(0);
}

preloadAvatar().catch(() => {});
boot().catch((e) => showError(e.message || 'Could not load setup. Refresh to try again.'));
