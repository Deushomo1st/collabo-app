// A round profile picture for a username, or their initial on a colour when they have none. Used beside names across the site.
// The picture is /api/users/{name}/avatar (revalidated by ETag); names that answered 404 are remembered so cards do not ask again.
import { h, profileHref } from '/js/services/dom.js';
import { avatarUrl } from '/js/services/api.js';

const none = new Set();
const hue = (name) => [...name].reduce((a, c) => (a * 31 + c.charCodeAt(0)) % 360, 7);

// Tapping anyone's picture opens their profile (pictures inside a link already do; pickers keep their own tap, and so does the page you are already on).
document.addEventListener('click', (e) => {
    const f = e.target.closest?.('.sp-face[title]');
    if (!f || f.closest('a, label, .gz-circle, .dangle, .speech, .ps-person, .pst-person')) return;
    const to = profileHref(f.title);
    if (new URL(to, location.href).href === location.href) return;
    e.preventDefault(); e.stopPropagation();
    location.href = to;
}, true);

export function face(name, cls = '') {
    const el = h('span', { class: `sp-avatar sp-face ${cls}`, style: `--hue:${hue(name)}`, title: name, text: name[0].toUpperCase() });
    if (!none.has(name)) {
        const img = h('img', { class: 'sp-face__img', src: avatarUrl(name), alt: '', loading: 'lazy' });
        img.addEventListener('error', () => { none.add(name); img.remove(); }, { once: true });
        el.append(img);
    }
    return el;
}
