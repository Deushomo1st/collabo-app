// The four-square menu (nav-mode.js): the main pages in one dialog. leave(go) lets a page with unsaved work ask before it leaves.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { currentUser } from '/js/services/api.js';
import { h, profileHref } from '/js/services/dom.js';
import { GEAR, HOME, CHAT, PERSON, svg } from '/js/services/icons.js';

const pages = (me) => [
    ['Gaze', '/HTML-pages/gaze.html', HOME],
    ['Yarns', '/HTML-pages/yarnspaces.html', CHAT],
    ['Notifications', '/HTML-pages/notifications.html', '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>'],
    ['Post', '/HTML-pages/create-post.html', '<path d="M12 5v14M5 12h14"/>'],
    ['Profile', profileHref(me.username), PERSON],
    ['Settings', '/HTML-pages/profile-settings.html', GEAR],
    ['Report a problem', '/HTML-pages/report.html', '<path d="M4 22V4h12l-1.5 4L16 12H4"/>'],
];

export async function openNavMenu(leave = (go) => go()) {
    const me = await currentUser().catch(() => null);
    if (!me) return;
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Menu', html: '<h3 class="glass-blur-dialog__title">Menu</h3><div class="nm-grid"></div>' });
    const here = location.pathname;
    panel.querySelector('.nm-grid').append(...pages(me).map(([name, href, icon]) => {
        const pic = h('span', { class: 'nm-icon' }); pic.innerHTML = svg(icon);
        const a = h('a', { class: 'nm-link', href }, pic, h('span', { text: name }));
        if (new URL(href, location.href).pathname === here) a.setAttribute('aria-current', 'page');
        a.addEventListener('click', (e) => { e.preventDefault(); close(); leave(() => { location.href = href; }); });
        return a;
    }));
}
