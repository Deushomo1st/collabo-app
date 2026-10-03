// Your settings, a page of its own (the gear on your profile) and the only one: appearance and themes, privacy, Yarns, the post or report you are writing, account security.
// Each segment is rows; a row opens its own sub-modal.
import '/js/services/live.js';
import { mountThemeRow, getTheme } from '/js/components/theme-switcher/theme-switcher.js';
import { securityRows } from '/js/components/account/security.js';
import { loadYarnLists, archivedCount, blockedCount, defaultChatName, openDefaultChat, openArchive, openBlocked } from '/js/components/settings/yarn-settings.js';
import { audienceName, postOptionsNow, openPostAudience, openPostOptions } from '/js/components/settings/post-settings.js';
import { postDefault } from '/js/services/stash.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { messagePrivacyRow } from '/js/services/message-privacy.js';
import { currentUser, logoutUser } from '/js/services/api.js';
import { h } from '/js/services/dom.js';
import { openNavPreference, navPreferenceName } from '/js/services/nav-preference.js';
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';

const PROFILE = '/HTML-pages/profile.html';
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
function toast(text) {
    const el = document.getElementById('toast');
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 1300);
}

// Each card is a segment. The header names the segment you are in: it starts on the first and follows you down the page.
function followSegments(segments) {
    const title = document.getElementById('header-title'), bar = document.querySelector('.sp-header');
    const spy = () => {
        const line = bar.getBoundingClientRect().bottom + 24;   // a segment counts once its top passes under the header
        const atEnd = innerHeight + scrollY >= document.documentElement.scrollHeight - 4;   // a short last segment still gets its turn
        const now = atEnd ? segments.at(-1) : segments.filter((s) => s.getBoundingClientRect().top <= line).at(-1) || segments[0];
        if (title.textContent !== now.dataset.segment) title.textContent = now.dataset.segment;
    };
    addEventListener('scroll', spy, { passive: true }); addEventListener('resize', spy);
    spy();
}

const segment = (id, name, ...rows) => h('section', { id, class: 'yn-group sp-glass yn-card sp-form', 'data-segment': name }, h('strong', { text: name }), ...rows);

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    mountMainNav(me.username, 'Settings');
    await loadYarnLists().catch(() => {});   // the Archive and Blocked counts; the lists say so themselves if they cannot load
    const sub = (name, now, open) => {   // a row that opens its own dialog; the line under the name says what is set
        const line = h('span', { class: 'yn-last', text: now() });
        return h('button', { class: 'yn-row', type: 'button', onclick: () => open(() => { line.textContent = now(); }) }, h('span', { class: 'yn-body' }, h('strong', { text: name }), line), h('span', { text: '›', 'aria-hidden': 'true' }));
    };
    const openTheme = async (onClose) => {
        const { panel } = await openGlassBlurDialog({ size: 'sm', label: 'Theme', onClose, html: '<h3 class="glass-blur-dialog__title">Theme</h3><div></div>' });
        await mountThemeRow(panel.lastElementChild);
    };
    const segments = [
        segment('appearance', 'Appearance and themes', sub('Theme', () => (getTheme() === 'light' ? 'Light' : 'Dusk'), openTheme), sub('Navigation preference', navPreferenceName, openNavPreference)),
        segment('yarns', 'Yarns', sub('Default chat', defaultChatName, openDefaultChat), sub('Archive', archivedCount, openArchive)),
        // what every new post starts from; the Create post page can change it for one post (a stored copy, so it stays on this device)
        segment('postdefaults', 'New posts', h('p', { class: 'yn-hint', text: 'Every new post starts like this. On the Create post page you can change it for just that post.' }),
            sub('Who can see it', () => audienceName(postDefault), (done) => openPostAudience(me.username, done, postDefault)), sub('On the post', () => postOptionsNow(postDefault), (done) => openPostOptions(done, postDefault))),
        segment('security', 'Security and privacy', await messagePrivacyRow(me.username, toast),
            h('p', { class: 'yn-hint', text: 'Only new conversations are limited. Chats you already have carry on.' }), sub('Blocklist', blockedCount, openBlocked),
            h('a', { class: 'yn-row', href: '/HTML-pages/history.html' }, h('span', { class: 'yn-body' }, h('strong', { text: 'History' }), h('span', { class: 'yn-last', text: 'Ideas you have seen on the Gaze' })), h('span', { text: '›', 'aria-hidden': 'true' })),
            ...securityRows(toast)),
    ].filter(Boolean);
    document.getElementById('section').replaceChildren(...segments,
        h('p', { class: 'yn-hint' }, `Signed in as ${me.username}. `, h('a', { href: PROFILE, text: 'Back to my profile' })),
        h('button', { class: 'yn-quick yn-signout', type: 'button', text: 'Sign out', onclick: async () => { await logoutUser().catch(() => {}); toLogin(); } }));
    followSegments(segments);
    await document.fonts?.ready;   // the text reflows when the fonts arrive, so scroll after
    if (location.hash.length > 1) document.getElementById(location.hash.slice(1))?.scrollIntoView();   // Yarns, the post and the report send you to their own segment
}
boot();
