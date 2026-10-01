// post-share: a tall rail of the people you talk to, over the page. Tap one to tick them; Done closes it.
// The post page uses it to pick who gets a post by yarn once it is published; a post card uses it to yarn an existing post right away.
//
//   import { openPostShare } from '/js/components/post-share/post-share.js';
//   openPostShare({ picked: new Set(), notes: { none: '…', some: (n) => `${n} picked.` }, onPick, onClose, onDone, onLink });
//
// picked: the Set of usernames this edits. notes: the line under the rail. onPick(): after every tick. onClose(): however it closed.
// onDone(picked): only when Done was pressed (not Escape or a tap outside). onLink(): the link circle at the top (defaults to a hint).
import { h, toast } from '/js/services/dom.js';
import { currentUser, yarnThreads, yarnDirectory, following, avatarUrl } from '/js/services/api.js';

const CSS_HREF = '/js/components/post-share/post-share.css';
if (!document.querySelector('link[data-component="post-share"]')) {
    const link = document.createElement('link');
    link.rel = 'stylesheet'; link.href = CSS_HREF; link.dataset.component = 'post-share';
    document.head.appendChild(link);
}

// Icons are fixed strings written here, never user text.
const ico = (paths) => { const s = h('span', { class: 'ps-ico', 'aria-hidden': 'true' }); s.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`; return s; };
const ICON = {
    search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
    link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
    check: '<path d="M5 12l5 5 9-10"/>',
};

let people = null;   // the people you talk to, loaded once
const hue = (name) => [...name].reduce((a, c) => (a * 31 + c.charCodeAt(0)) % 360, 7);

function face(p) {
    const el = h('span', { class: 'sp-avatar ps-face', style: `--hue:${hue(p.username)}`, text: p.username[0].toUpperCase() });
    const img = h('img', { src: avatarUrl(p.username, p.avatar), alt: '', loading: 'lazy' });
    img.addEventListener('error', () => img.remove());
    el.append(img);
    return el;
}

async function loadPeople() {
    if (people) return people;
    const me = await currentUser();
    const [threads, follows] = await Promise.all([yarnThreads('inbox', 'MYSPACE').catch(() => []), following(me.username).catch(() => [])]);
    const seen = new Map();
    for (const t of threads) {
        if (t.status === 'DECLINED') continue;
        for (const m of t.members) if (m.username !== me.username && !seen.has(m.username)) seen.set(m.username, { username: m.username, avatar: m.avatar });
    }
    for (const f of Array.isArray(follows) ? follows : []) if (!seen.has(f.username)) seen.set(f.username, { username: f.username, avatar: null });
    return (people = [...seen.values()]);
}

export async function openPostShare({ picked, notes, onPick, onClose, onDone, onLink } = {}) {
    const opener = document.activeElement;
    const list = h('div', { class: 'ps-people' });
    const note = h('p', { class: 'ps-share__note' });
    const search = h('input', { class: 'ps-search__in', placeholder: 'Search people', 'aria-label': 'Search people', autocomplete: 'off' });
    const searchBtn = h('button', { class: 'ps-circle', type: 'button', 'aria-label': 'Search people' }, ico(ICON.search));
    const searchBox = h('div', { class: 'ps-search' }, searchBtn, search);
    const linkBtn = h('button', { class: 'ps-circle', type: 'button', 'aria-label': 'Copy link', onclick: onLink || (() => toast('The link exists once the post is up. Copy it from the post itself.')) }, ico(ICON.link));
    const done = h('button', { class: 'gz-nav gz-nav--brand', type: 'button', text: 'Done' });
    const rail = h('div', { class: 'ps-rail' }, h('div', { class: 'ps-rail__head' }, searchBox, linkBtn), list);
    const overlay = h('div', { class: 'ps-share', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'Share with' }, rail, h('div', { class: 'ps-share__foot' }, note, done));
    let extra = [];   // people found by search who you have not talked to yet

    const close = () => {
        overlay.classList.remove('is-on');
        setTimeout(() => { overlay.remove(); document.removeEventListener('keydown', onKey); opener?.focus?.(); }, 220);
        onClose?.();
    };
    const onKey = (e) => { if (e.key === 'Escape') close(); };
    const draw = () => {
        const q = search.value.trim().toLowerCase();
        const all = [...(people || []), ...extra.filter((x) => !(people || []).some((p) => p.username === x.username))];
        const shown = all.filter((p) => !q || p.username.toLowerCase().includes(q));
        list.replaceChildren(...(shown.length ? shown.map((p) => h('button', {
            class: `ps-person${picked.has(p.username) ? ' is-on' : ''}`, type: 'button', 'aria-pressed': String(picked.has(p.username)), title: p.username,
            onclick: () => {
                if (picked.has(p.username)) picked.delete(p.username); else picked.add(p.username);
                if (!(people || []).some((x) => x.username === p.username)) people.push(p);
                onPick?.(); draw();
            } }, face(p), h('span', { class: 'ps-person__n', text: p.username }), h('span', { class: 'ps-person__tick' }, ico(ICON.check))))
            : [h('p', { class: 'ps-share__empty', text: q ? 'No one by that name yet.' : 'No yarnmates yet. Search for someone.' })]));
        note.textContent = picked.size ? notes.some(picked.size) : notes.none;
    };

    let timer;
    search.addEventListener('input', () => {
        draw(); clearTimeout(timer);
        const q = search.value.trim();
        if (q.length < 2) return;
        timer = setTimeout(async () => { try { extra = await yarnDirectory(q); draw(); } catch { /* the local list still works */ } }, 250);
    });
    searchBtn.addEventListener('click', () => { rail.classList.add('is-wide'); searchBox.classList.add('is-open'); search.focus(); });
    done.addEventListener('click', () => { onDone?.(picked); close(); });
    overlay.addEventListener('pointerdown', (e) => { if (e.target === overlay) close(); });
    document.addEventListener('keydown', onKey);
    document.body.append(overlay);
    requestAnimationFrame(() => overlay.classList.add('is-on'));
    done.focus();
    note.textContent = 'Loading…';
    try { await loadPeople(); } catch (err) { toast(err.message); close(); return; }
    draw();
}
