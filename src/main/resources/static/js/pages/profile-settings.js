// Your settings, a page of its own (the gear on your profile): theme, who can message you, account security.
import '/js/services/live.js';
import { mountThemeSwitcher, mountThemeRow } from '/js/components/theme-switcher/theme-switcher.js';
import { securityRows } from '/js/components/account/security.js';
import { mountSettingsNav } from '/js/services/settings-nav.js';
import { messagePrivacyRow } from '/js/services/message-privacy.js';
import { currentUser, logoutUser } from '/js/services/api.js';
import { h } from '/js/services/dom.js';

const PROFILE = '/HTML-pages/profile.html';
const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
function toast(text) {
    const el = document.getElementById('toast');
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2600);
}

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return toLogin();
    mountThemeSwitcher('#theme-slot', { inline: true, collapse: true });
    mountSettingsNav(PROFILE);
    const theme = h('div', { class: 'yn-theme' });
    document.getElementById('section').replaceChildren(
        h('section', { class: 'yn-group sp-glass yn-card sp-form' }, theme, await messagePrivacyRow(me.username, toast)),
        h('section', { class: 'yn-group sp-glass yn-card sp-form' }, ...securityRows(toast)),
        h('p', { class: 'yn-hint' }, `Signed in as ${me.username}. `, h('a', { href: PROFILE, text: 'Back to my profile' })),
        h('button', { class: 'yn-quick yn-signout', type: 'button', text: 'Sign out', onclick: async () => { await logoutUser().catch(() => {}); toLogin(); } }));
    await mountThemeRow(theme);
}
boot();
