// History: the ideas you have already seen on the Gaze (kept on this device), with a search over their words and usernames.
import { mountSettingsNav } from '/js/services/settings-nav.js';
import { postCard } from '/js/components/post-card/post-card.js';
import { historyRead, historyDrop, historyClear, historySearch } from '/js/services/history.js';
import { currentUser } from '/js/services/api.js';
import { h } from '/js/services/dom.js';

const SETTINGS = '/HTML-pages/profile-settings.html';
const list = () => document.getElementById('list');

async function boot() {
    const me = await currentUser().catch(() => null);
    if (!me) return location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
    mountSettingsNav(SETTINGS);
    const q = document.getElementById('hs-q');
    const draw = () => {
        const all = historyRead(me.username), shown = historySearch(all, q.value.trim());
        const cards = shown.map((p) => { const c = postCard(p, { onGone: () => { historyDrop(me.username, p.id); draw(); } }); c.dataset.id = p.id; return c; });
        const note = !all.length ? 'Ideas you have seen on the Gaze will be listed here.' : !shown.length ? `Nothing in your history matches "${q.value.trim()}".` : '';
        const clear = h('button', { class: 'yn-quick', type: 'button', text: 'Clear history', onclick: () => { if (confirm('Clear your history on this device?')) { historyClear(me.username); draw(); } } });
        list().replaceChildren(...cards, ...(note ? [h('p', { class: 'gz-empty', text: note })] : []), ...(all.length && !q.value.trim() ? [clear] : []));
    };
    let timer;
    q.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(draw, 200); });
    document.getElementById('hs-search').addEventListener('submit', (e) => { e.preventDefault(); draw(); });
    draw();
    const top = document.querySelector('.sp-top'), mark = () => top.classList.toggle('is-scrolled', scrollY > 8);
    addEventListener('scroll', mark, { passive: true }); mark();   // the header's blurred backing, as on the Gaze
}
boot();
