// A round profile picture for a username, or their initial on a colour when they have none. Used beside names across the site.
// The picture is /api/users/{name}/avatar (revalidated by ETag); names that answered 404 are remembered so cards do not ask again.
import { h } from '/js/services/dom.js';
import { avatarUrl } from '/js/services/api.js';

const none = new Set();
const hue = (name) => [...name].reduce((a, c) => (a * 31 + c.charCodeAt(0)) % 360, 7);

export function face(name, cls = '') {
    const el = h('span', { class: `sp-avatar sp-face ${cls}`, style: `--hue:${hue(name)}`, title: name, text: name[0].toUpperCase() });
    if (!none.has(name)) {
        const img = h('img', { class: 'sp-face__img', src: avatarUrl(name), alt: '', loading: 'lazy' });
        img.addEventListener('error', () => { none.add(name); img.remove(); }, { once: true });
        el.append(img);
    }
    return el;
}
