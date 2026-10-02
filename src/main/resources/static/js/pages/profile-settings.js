// Your settings, a page of its own (the gear on your profile): appearance and themes, privacy, account security.
import '/js/services/live.js';
import { mountThemeRow, getTheme } from '/js/components/theme-switcher/theme-switcher.js';
import { securityRows } from '/js/components/account/security.js';
import { mountSettingsNav } from '/js/services/settings-nav.js';
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
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2600);
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

const segment = (name, ...rows) => h('section', { class: 'yn-group sp-glass yn-card sp-form', 'data-segment': name }, h('strong', { text: name }), ...rows);

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    mountSettingsNav(PROFILE);
    const sub = (name, now, open) => {   // a row under "Appearance and themes" that opens its own dialog; the line under the name says what is set
        const line = h('span', { class: 'yn-last', text: now() });
        return h('button', { class: 'yn-row', type: 'button', onclick: () => open(() => { line.textContent = now(); }) }, h('span', { class: 'yn-body' }, h('strong', { text: name }), line), h('span', { text: '›', 'aria-hidden': 'true' }));
    };
    const openTheme = async (onClose) => {
        const { panel } = await openGlassBlurDialog({ size: 'sm', label: 'Theme', onClose, html: '<h3 class="glass-blur-dialog__title">Theme</h3><div></div>' });
        await mountThemeRow(panel.lastElementChild);
    };
    const segments = [
        segment('Appearance and themes', sub('Theme', () => (getTheme() === 'light' ? 'Light' : 'Dusk'), openTheme), sub('Navigation preference', navPreferenceName, openNavPreference)),
        segment('Privacy', await messagePrivacyRow(me.username, toast)),
        segment('Account security', ...securityRows(toast)),
    ];
    document.getElementById('section').replaceChildren(...segments,
        h('p', { class: 'yn-hint' }, `Signed in as ${me.username}. `, h('a', { href: PROFILE, text: 'Back to my profile' })),
        h('button', { class: 'yn-quick yn-signout', type: 'button', text: 'Sign out', onclick: async () => { await logoutUser().catch(() => {}); toLogin(); } }));
    followSegments(segments);
}
boot();
