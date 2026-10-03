// The four-square menu (nav-mode.js): the main pages in one dialog. leave(go) lets a page with unsaved work ask before it leaves.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { currentUser } from '/js/services/api.js';
import { h } from '/js/services/dom.js';
import { DEST } from '/js/services/destinations.js';
import { svg } from '/js/services/icons.js';
import { unreadYarns, newPosts, unreadNotes } from '/js/services/message-notice.js';

const NAMES = { Report: 'Report a problem' };   // the rest are called what destinations.js calls them
const pages = (me) => Object.entries(DEST).map(([k, d]) => [NAMES[k] ?? k, d.href(me.username), d.icon]);

export async function openNavMenu(leave = (go) => go()) {
    const me = await currentUser().catch(() => null);
    if (!me) return;
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', className: 'nm-panel', label: 'Menu', html: '<h3 class="glass-blur-dialog__title">Menu</h3><div class="nm-grid"></div>' });
    const here = location.pathname;
    const dots = { Yarns: await unreadYarns().catch(() => 0), Gaze: await newPosts(me.username), Notifications: await unreadNotes().catch(() => 0) };   // the numbered dots: new yarns, new posts, unread notifications
    panel.querySelector('.nm-grid').append(...pages(me).map(([name, href, icon]) => {
        const pic = h('span', { class: 'nm-icon' }); pic.innerHTML = svg(icon);
        const a = h('a', { class: 'nm-link', href }, pic, h('span', { class: 'nm-name', text: name }), dots[name] > 0 && h('span', { class: 'nm-badge', text: dots[name] > 99 ? '99+' : String(dots[name]), 'aria-label': `${dots[name]} new` }));
        if (name !== 'Help' && new URL(href, location.href).pathname === here) a.setAttribute('aria-current', 'page');
        a.addEventListener('click', (e) => { e.preventDefault(); close(); if (name === 'Help') import('/js/services/help.js').then((m) => m.openHelp()); else leave(() => { location.href = href; }); });   // Help is a dialog on this page, so there is nothing to leave
        return a;
    }));
}
