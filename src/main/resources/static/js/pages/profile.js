// Profile page: identity, links, follow, Credentials / Feats tabs, edit dialog. Reached as profile.html?u=<username>
// (no ?u= shows your own). All network calls live in js/services/api.js; text goes in through textContent only.
import '/js/services/live.js';   // keeps the live socket open, so yarns sent to you are acknowledged as delivered from any page
import { mountThemeSwitcher, mountThemeRow } from '/js/components/theme-switcher/theme-switcher.js';
import { openGlassBlurDialog, preloadGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { createAvatarCard, openAvatarUpload, preloadAvatar } from '/js/components/avatar/avatar.js';
import { postCard } from '/js/components/post-card/post-card.js';
import { openMine } from '/js/components/applications/applications.js';
import { openDrafts } from '/js/components/drafts/drafts.js';
import { messagePrivacyRow } from '/js/services/message-privacy.js';
import { removalRecordsSection } from '/js/components/profile/removal-records.js';
import {
    currentUser, profileGet, profileUpdate, profileLinks, avatarUrl, avatarSave, avatarRemove,
    follow, unfollow, followers, following, credentialsOf, credentialFeature, credentialShip, credentialUnship, userPosts, postGet, mediaUrl, yarnStartMySpace,
} from '/js/services/api.js';

const MAX_LINKS = 12, MAX_SHIPPED = 5;
const PRIVACY = [
    ['EVERYONE', 'Everyone'], ['FOLLOWERS', 'People who follow me'], ['FOLLOWING', 'People I follow'],
    ['MUTUAL', 'Mutual follows'], ['APPLICANTS', 'Only people who applied to my posts'],
];
const KIND = { SPACE_FORMED: 'Space formed', MILESTONE_CREDITED: 'Milestone' };

let profile = null;      // ProfileResponse of the person being viewed
let credentials = null;  // CredentialsResponse, loaded with the profile
let tab = 'posts';
let sort = 'latest';    // Latest / Popular / Oldest, applied to the posts already loaded (ponytail: popular covers what is loaded, not the whole history)
let viewer = '';        // the signed-in username
let feeds = {};         // Posts / Reposts pages loaded so far: { posts: { items, next, error }, reposts: {...} }

// ---- tiny DOM helper -------------------------------------------------------
function h(tag, props = {}, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'text') el.textContent = v;
        else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
        else if (k === 'style') el.style.cssText = v;
        else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) if (kid != null && kid !== false) el.append(kid);
    return el;
}
function toast(text) {
    const el = document.getElementById('toast');
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2600);
}
const section = () => document.getElementById('section');
const usernameInUrl = () => new URLSearchParams(location.search).get('u');
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
const day = (iso) => new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
// Only http(s) links become clickable (the server enforces this too; this keeps a bad row from ever becoming a javascript: link).
const safeHref = (u) => (/^https?:\/\//i.test(u) ? u : null);
const externalLink = (props, ...kids) => h('a', { ...props, href: safeHref(props.href), target: '_blank', rel: 'noopener noreferrer' }, ...kids);

// ---- loading ---------------------------------------------------------------
async function load(name) {
    feeds = {};
    [profile, credentials] = await Promise.all([profileGet(name), credentialsOf(name)]);
    document.getElementById('header-title').textContent = profile.username;
    document.title = `COLLABO — ${profile.username}`;
    render();
}

function fail(err) {
    if (err.status === 401) return toLogin();
    section().replaceChildren(
        h('p', { class: 'pf-empty', text: err.status === 404 ? 'No one has that username.' : (err.message || 'Could not load this profile.') }),
        err.status === 404 ? null : h('button', { class: 'pf-btn', type: 'button', text: 'Try again', onclick: boot }));
}

// ---- identity card ---------------------------------------------------------
function picture() {
    const name = profile.username;
    return createAvatarCard({
        src: profile.avatarVersion ? avatarUrl(name, profile.avatarVersion) : null,
        name, showName: false, editable: profile.self,
        onClick: profile.self ? () => openAvatarUpload({
            current: profile.avatarVersion ? avatarUrl(name, profile.avatarVersion) : null,
            onSave: async (_dataUrl, blob) => {
                try { profile = blob ? await avatarSave(blob) : await avatarRemove(); render(); }
                catch (err) { toast(err.message); }
            },
        }) : undefined,
    });
}

async function toggleFollow() {
    try { profile = await (profile.follow.iFollow ? unfollow : follow)(profile.username); render(); }
    catch (err) { toast(err.message); }
}

function identity() {
    const f = profile.follow;
    const count = (n, label, load) => h('button', { class: 'pf-count', type: 'button', onclick: () => openPeople(label, load) },
        h('strong', { text: String(n) }), ' ', h('span', { text: label }));
    return h('section', { class: 'pf-card sp-glass' },
        picture(),
        h('div', { class: 'pf-id' },
            h('div', { class: 'pf-name' },
                h('h2', { text: profile.username }),
                profile.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: profile.preferredTitle }),
                f.followsMe && !profile.self && h('span', { class: 'sp-tag sp-tag--muted', text: 'Follows you' })),
            profile.bio ? h('p', { class: 'pf-bio', text: profile.bio })
                : h('p', { class: 'pf-bio is-empty', text: profile.self ? 'Add a short bio so people know what you build.' : 'No bio yet.' }),
            h('div', { class: 'pf-counts' }, count(f.followers, 'followers', followers), count(f.following, 'following', following)),
            profile.links.length > 0 && h('div', { class: 'pf-links' }, ...profile.links.map((l) =>
                externalLink({ class: 'pf-link', href: l.url }, h('strong', { text: l.title }), h('small', { text: l.note || l.url })))),
            h('div', { class: 'pf-actions' },
                profile.self && h('button', { class: 'pf-btn pf-btn--brand', type: 'button', text: 'Edit profile', onclick: openEdit }),
                profile.self && h('button', { class: 'pf-btn', type: 'button', text: 'My applications', onclick: openMine }),
                profile.self && h('button', { class: 'pf-btn', type: 'button', text: 'Drafts', onclick: openDrafts }),
                profile.self && h('button', { class: 'pf-btn', type: 'button', text: 'Settings', onclick: openProfileSettings }),
                !profile.self && f.canFollow && h('button', { class: 'pf-btn', type: 'button', text: 'Message', onclick: openMessage }),
                !profile.self && f.canFollow && h('button', { class: `pf-btn ${f.iFollow ? '' : 'pf-btn--brand'}`, type: 'button', text: f.iFollow ? 'Following' : 'Follow', onclick: toggleFollow })),
            h('span', { class: 'pf-joined', text: `Joined ${day(profile.joined)}` })));
}

async function openPeople(label, load) {
    const { panel } = await openGlassBlurDialog({ size: 'sm', label, html: `<h3 class="glass-blur-dialog__title"></h3><ul class="pf-people"></ul>` });
    panel.querySelector('h3').textContent = `${profile.username} · ${label}`;
    const ul = panel.querySelector('ul');
    try {
        const people = await load(profile.username);
        if (people.length === 0) ul.append(h('li', { class: 'pf-hint', text: 'No one yet.' }));
        for (const p of people) {
            ul.append(h('li', {}, h('a', { href: `/HTML-pages/profile.html?u=${encodeURIComponent(p.username)}` },
                h('strong', { text: p.username }), p.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: p.preferredTitle }))));
        }
    } catch (err) { ul.append(h('li', { class: 'pf-error', text: err.message })); }
}

// ---- credentials / feats ---------------------------------------------------
async function change(action) {
    try {
        const entry = await action();
        credentials = { ...credentials, entries: credentials.entries.map((e) => (e.id === entry.id ? entry : e)) };
        renderTabs();
    } catch (err) { toast(err.message); }
}

async function addShipped(entry) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Add proof', html:
        '<h3 class="glass-blur-dialog__title">What did you ship?</h3><form class="sp-form"></form>' });
    const title = h('input', { class: 'sp-input', maxlength: 60, required: true, placeholder: 'Landing page, repo, demo…' });
    const url = h('input', { class: 'sp-input', type: 'url', maxlength: 500, required: true, placeholder: 'https://' });
    const err = h('p', { class: 'pf-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(h('label', {}, 'Title', title), h('label', {}, 'Link', url), err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Attach')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await change(() => credentialShip(entry.id, title.value, url.value)); close(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

function entryCard(e) {
    const shipped = e.shipped.map((s) => h('span', { class: 'pf-shipped' },
        externalLink({ href: s.url }, s.title),
        profile.self && h('button', { class: 'pf-x', type: 'button', 'aria-label': `Remove ${s.title}`, text: '×', onclick: () => change(() => credentialUnship(e.id, s.id)) })));
    return h('article', { class: 'pf-entry sp-glass' },
        h('div', { class: 'pf-entry__top' },
            h('strong', { text: e.title }),
            e.featured && h('span', { class: 'sp-tag sp-tag--warn', text: 'Feat' }),
            h('span', { class: 'sp-tag', text: KIND[e.kind] || e.kind }),
            h('time', { datetime: e.occurredAt, text: day(e.occurredAt) })),
        e.detail && h('p', { text: e.detail }),
        e.nothingShipped ? h('span', { class: 'sp-tag sp-tag--muted', text: 'Nothing shipped' }) : h('div', { class: 'pf-shipped' }, ...shipped),
        profile.self && h('div', { class: 'pf-entry__tools' },
            h('button', { class: 'pf-btn', type: 'button', text: e.featured ? 'Remove from feats' : 'Make a feat', onclick: () => change(() => credentialFeature(e.id, !e.featured)) }),
            e.shipped.length < MAX_SHIPPED && h('button', { class: 'pf-btn', type: 'button', text: 'Add proof', onclick: () => addShipped(e) })));
}

async function loadFeed(id, more) {
    const f = feeds[id] || (feeds[id] = { items: [], next: null });
    try {
        const page = await userPosts(profile.username, id, more ? f.next : undefined);
        f.items = more ? [...f.items, ...page.items] : page.items;
        f.next = page.next || null; f.error = null;
    } catch (err) { f.error = err.message; }
    renderTabs();
}

function postList(id) {
    const f = feeds[id];
    if (!f) { loadFeed(id, false); return h('p', { class: 'pf-empty', text: 'Loading…' }); }
    if (f.error) return h('p', { class: 'pf-empty', text: f.error });
    if (f.items.length === 0) return h('p', { class: 'pf-empty', text: id === 'posts'
        ? (profile.self ? 'Ideas you post to The Gaze show up here.' : 'No posts yet.')
        : (profile.self ? 'Ideas you shout out show up here.' : 'No reposts yet.') });
    return h('div', {}, h('div', { class: 'pf-grid' }, ...sorted(f.items).map((p) => tile(p, f))),
        f.next && h('button', { class: 'pf-btn pf-more', type: 'button', text: 'Show more', onclick: () => loadFeed(id, true) }));
}

const eye = () => { const i = h('span', { class: 'pf-eye', 'aria-hidden': 'true' }); i.innerHTML = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>'; return i; };   // fixed markup, no user text

async function openPost(p, f) {   // opening it counts a view, the same as a shared link
    try {
        const full = await postGet(p.id);
        const { panel, close } = await openGlassBlurDialog({ size: 'lg', label: 'Post', html: '<div class="gz-linked"></div>' });
        panel.querySelector('.gz-linked').append(postCard(full, { onGone: () => { close(); f.items = f.items.filter((x) => x.id !== p.id); renderTabs(); } }));
    } catch (err) { toast(err.message); }
}

function tile(p, f) {
    const pic = p.media?.find((m) => m.kind !== 'VIDEO');
    return h('button', { class: 'pf-tile', type: 'button', onclick: () => openPost(p, f) },
        pic ? h('img', { src: mediaUrl(pic.id), alt: '', loading: 'lazy' }) : h('span', { class: 'pf-tile__text' }, h('strong', { text: p.title }), h('span', { text: p.body })),
        h('span', { class: 'pf-tile__views', title: 'Times someone else opened this', onclick: (e) => e.stopPropagation() }, eye(), String(p.views)));   // a statistic only: tapping it does nothing
}

const SORTS = [['latest', 'Latest'], ['popular', 'Popular'], ['oldest', 'Oldest']];
function sorted(items) {
    if (sort === 'oldest') return [...items].reverse();
    if (sort === 'popular') return [...items].sort((a, b) => b.views - a.views);
    return items;
}

function renderTabs() {
    const box = document.getElementById('tabs-box');
    const all = credentials.entries, feats = all.filter((e) => e.featured);
    const tabBtn = (id, label) => h('button', {
        class: 'pf-tab', role: 'tab', type: 'button', 'aria-selected': String(tab === id), text: label,
        onclick: () => { tab = id; renderTabs(); },
    });
    const postsTab = tab === 'posts' || tab === 'reposts';
    let body;
    if (postsTab) body = postList(tab);
    else if (tab === 'removals') body = removalRecordsSection(profile, viewer);
    else if (!credentials.visible) body = h('p', { class: 'pf-empty', text: 'Credentials are private.' });
    else {
        const shown = tab === 'feats' ? feats : all;
        body = shown.length === 0
            ? h('p', { class: 'pf-empty', text: tab === 'feats'
                ? (profile.self ? 'Promote a credential to show it off here.' : 'No feats yet.')
                : (profile.self ? 'Credentials appear when you form a space or hit a milestone.' : 'No credentials yet.') })
            : h('div', { class: 'pf-list' }, ...shown.map(entryCard));
    }
    box.replaceChildren(
        h('div', { class: 'pf-bar' },
            h('div', { class: 'pf-tabs', role: 'tablist' }, tabBtn('posts', 'Posts'), tabBtn('reposts', 'Reposts'), tabBtn('removals', 'Removals'),
                tabBtn('credentials', `Credentials · ${credentials.visible ? all.length : 0}`), tabBtn('feats', `Feats · ${credentials.visible ? feats.length : 0}`)),
            postsTab && h('div', { class: 'pf-sort', role: 'group', 'aria-label': 'Sort' }, ...SORTS.map(([k, label]) =>
                h('button', { class: 'pf-sort__btn', type: 'button', 'aria-pressed': String(sort === k), text: label, onclick: () => { sort = k; renderTabs(); } })))),
        h('div', { class: 'pf-stage sp-glass' }, body));
}

function render() {
    section().replaceChildren(identity(), h('div', { id: 'tabs-box', class: 'pf-list' }));
    renderTabs();
}

// ---- edit dialog: title, bio, links, who sees credentials ---------------------
async function openEdit() {
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: 'Edit profile', html:
        '<h3 class="glass-blur-dialog__title">Edit profile</h3><form class="sp-form"></form>' });
    const title = h('input', { class: 'sp-input', id: 'pf-title', maxlength: 40, value: profile.preferredTitle || '', placeholder: 'e.g. Backend developer' });
    const bio = h('textarea', { class: 'sp-input', id: 'pf-bio', rows: 4, maxlength: 600, placeholder: 'Up to 60 words' }, profile.bio || '');
    const privacy = h('select', { class: 'sp-input', id: 'pf-privacy' }, ...PRIVACY.map(([v, label]) => h('option', { value: v, text: label, selected: v === profile.credentialsPrivacy })));
    const rows = h('div', { class: 'pf-links-edit' });
    const err = h('p', { class: 'pf-error', hidden: true });

    const addRow = (l = { title: '', url: '', note: '' }) => {
        const row = h('div', { class: 'pf-links-edit__row' },
            h('input', { class: 'sp-input', maxlength: 60, value: l.title, placeholder: 'Title', 'aria-label': 'Link title' }),
            h('input', { class: 'sp-input', maxlength: 500, value: l.url, placeholder: 'https://', 'aria-label': 'Link address' }),
            h('input', { class: 'sp-input', maxlength: 120, value: l.note || '', placeholder: 'Note (optional)', 'aria-label': 'Link note' }),
            h('button', { class: 'pf-x', type: 'button', 'aria-label': 'Remove link', text: '×', onclick: () => row.remove() }));
        rows.append(row);
    };
    profile.links.forEach(addRow);
    const linksFromForm = () => [...rows.children].map((r) => { const [t, u, n] = r.querySelectorAll('input'); return { title: t.value, url: u.value, note: n.value }; })
        .filter((l) => l.title.trim() || l.url.trim());

    panel.querySelector('form').append(
        h('label', { for: 'pf-title' }, 'Title', title),
        h('label', { for: 'pf-bio' }, 'Bio', bio),
        h('label', {}, 'Links', rows),
        h('button', { class: 'pf-btn', type: 'button', text: 'Add a link', onclick: () => { if (rows.children.length < MAX_LINKS) addRow(); } }),
        h('label', { for: 'pf-privacy' }, 'Who can see my credentials', privacy),
        err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Save')));

    panel.querySelector('form').addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await profileUpdate({ preferredTitle: title.value, bio: bio.value, credentialsPrivacy: privacy.value });
            profile = await profileLinks(linksFromForm());
            close(); render(); toast('Profile saved.');
        } catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

// ---- message: starts a MySpace yarn (the server refuses across a block) ------
async function openMessage() {
    const { panel } = await openGlassBlurDialog({ size: 'sm', label: 'Message', html: '<h3 class="glass-blur-dialog__title"></h3><form class="sp-form"></form>' });
    panel.querySelector('h3').textContent = `Message ${profile.username}`;
    const body = h('textarea', { class: 'sp-input', rows: 3, maxlength: 2000, placeholder: 'Your first yarn', required: true });
    const err = h('p', { class: 'pf-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(body, err, h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Send')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { const t = await yarnStartMySpace(profile.username, body.value); location.href = `/HTML-pages/yarnspaces.html#t/${t.id}`; }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

// ---- boot ------------------------------------------------------------------
async function boot() {
    try {
        const me = await currentUser();
        if (!me) return toLogin();
        viewer = me.username;
        await load(usernameInUrl() || me.username);
    } catch (err) { fail(err); }
}

preloadGlassBlurDialog(); preloadAvatar().catch(() => {});
async function openProfileSettings() {
    const { panel } = await openGlassBlurDialog({ size: 'sm', label: 'Settings', html: '<h3 class="glass-blur-dialog__title">Settings</h3><div class="pf-settings"></div>' });
    await mountThemeRow(panel.querySelector('.pf-settings'));
    panel.querySelector('.pf-settings').append(await messagePrivacyRow(viewer, toast));
}

mountThemeSwitcher('#theme-slot', { inline: true, collapse: true });
document.getElementById('header-back').addEventListener('click', (e) => {
    if (history.length > 1) { e.preventDefault(); history.back(); }
});
boot();
