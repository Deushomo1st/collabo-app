// The four-square menu (nav-mode.js): the main pages in one dialog. leave(go) lets a page with unsaved work ask before it leaves.
// It is ready before it is asked for (warm() runs once the page is idle), so the tap opens it at once and only the numbered dots come a moment later.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { currentUser } from '/js/services/api.js';
import { h } from '/js/services/dom.js';
import { DEST } from '/js/services/destinations.js';
import { svg } from '/js/services/icons.js';
import { unreadYarns, newPosts, unreadNotes } from '/js/services/message-notice.js';

const NAMES = { Report: 'Report a problem' };   // the rest are called what destinations.js calls them
const pages = (me) => Object.entries(DEST).map(([k, d]) => [NAMES[k] ?? k, d.href(me.username), d.icon]);

let who;   // the signed-in account, asked for once per page
const user = () => (who ??= currentUser().catch(() => null));

/** Asks for the account ahead of the tap. */
export const warm = () => { user(); };

export async function openNavMenu(leave = (go) => go()) {
    const me = await user();
    if (!me) return;
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', className: 'nm-panel', label: 'Menu', html: '<h3 class="glass-blur-dialog__title">Menu</h3><div class="nm-grid"></div>' });
    const here = location.pathname, links = {};
    panel.querySelector('.nm-grid').append(...pages(me).map(([name, href, icon]) => {
        const pic = h('span', { class: 'nm-icon' }); pic.innerHTML = svg(icon);
        const a = links[name] = h('a', { class: 'nm-link', href }, pic, h('span', { class: 'nm-name', text: name }));
        if (name !== 'Help' && new URL(href, location.href).pathname === here) a.setAttribute('aria-current', 'page');
        a.addEventListener('click', (e) => { e.preventDefault(); close(); if (name === 'Help') import('/js/services/help.js').then((m) => m.openHelp()); else leave(() => { location.href = href; }); });   // Help is a dialog on this page, so there is nothing to leave
        return a;
    }));
    // The numbered dots (new yarns, new posts, unread notifications) arrive when counted; the menu does not wait for them.
    const dot = (name, n) => { if (n > 0) links[name]?.append(h('span', { class: 'nm-badge', text: n > 99 ? '99+' : String(n), 'aria-label': `${n} new` })); };
    unreadYarns().then((n) => dot('Yarns', n), () => {});
    newPosts(me.username).then((n) => dot('Gaze', n), () => {});
    unreadNotes().then((n) => dot('Notifications', n), () => {});
}
