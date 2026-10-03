// yarn-thread: a conversation (history + composer) that refreshes itself when the server says something changed, and polls slowly as a backup.
// Your own yarns carry ticks: one = saved, two = reached every other member's browser, glowing green = every other member has opened it.
//
// It never talks to the network itself: you hand it load/send functions (from js/services/api.js).
//
// Usage:
//   import { createYarnThread, preloadYarnThread } from '/js/components/yarn-thread/yarn-thread.js';
//   await preloadYarnThread();
//   const t = createYarnThread({
//       meId, showNames: true,
//       load: (before) => yarnHistory(id, before),   // -> yarns, NEWEST first
//       send: (body) => yarnSend(id, body),          // -> the created yarn
//       onRead: () => yarnMarkRead(id),
//       signals: (refresh) => unsubscribe,           // optional: call refresh() when the server signals a change
//       isLive: () => true,                           // optional: while true, the backup poll slows down
//       unread: 3,                                    // optional: how many yarns were unread on opening; the view opens on the first of them
//       avatar: (name) => element,                   // optional: a picture beside their yarns (the component never fetches one itself)
//       onPin: (yarn) => Promise, canPin: () => bool,                    // optional: lets the reader pin/unpin a yarn (a Workspace room, for those allowed to)
//       avatarOn: 'every',                            // 'every' (default; group chats) or 'latest' (only their newest yarn; one-to-one chats)
//   });
//   host.append(t.element);  ...  t.destroy();      // destroy() stops the polling

import { zoomOf } from '/js/services/ui-scale.js';
import { face } from '/js/services/face.js';
import { skeletonBubbles } from '/js/services/skeleton.js';
const CSS_HREF = '/js/components/yarn-thread/yarn-thread.css';
const PAGE = 50;

export function preloadYarnThread() {
    return loadStylesOnce(CSS_HREF, 'yarn-thread');
}

// A post's address in a yarn, whether the app sent it (a shared post) or someone pasted it, becomes a card for the post instead of a link.
// The words around it stay plain text. opts.postInfo(id) -> the post (title, author, body, media); without it, or if it fails, the card just says "Open the post".
const POST_LINK = /((?:https?:\/\/[^\s/]+)?\/HTML-pages\/(?:view-post\.html\?id|gaze\.html\?post)=[0-9a-f-]{36})/;
const seen = new Map();   // post id -> the post, so a redrawn chat does not ask again

// opts: { meId, showNames, postInfo(id), load(before), send(body), onRead(), signals(refresh), isLive(), pollMs = 6000, disabledReason, onError(err) }
// Returns { element, refresh(), destroy(), setDisabledReason(text) }.
export function createYarnThread(opts) {
    preloadYarnThread().catch(() => {});
    let items = [];          // oldest -> newest
    let more = false;
    let dead = false;
    let lastRefresh = 0;

    const list = h('div', { class: 'yarn-thread__list', 'aria-live': 'polite' });
    const error = h('p', { class: 'yarn-thread__error', role: 'alert', hidden: true });
    const box = h('textarea', { class: 'yarn-thread__input', rows: '1', placeholder: 'Write a yarn', maxlength: '2000', 'aria-label': 'Write a yarn' });
    const sendBtn = h('button', { class: 'yarn-thread__send', type: 'button', 'aria-label': 'Send yarn' });
    sendBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>';
    const notice = h('p', { class: 'yarn-thread__notice', hidden: true });
    const composer = h('div', { class: 'yarn-thread__composer' }, box, sendBtn);
    const jump = h('button', { class: 'yarn-thread__jump', type: 'button', hidden: true, text: 'Newer yarns ↓', onclick: () => toBottom() });
    const stage = h('div', { class: 'yarn-thread__stage' }, list, jump);
    const element = h('section', { class: 'yarn-thread' }, stage, error, notice, composer);

    function fail(err) {
        error.textContent = err?.message || 'Something went wrong.';
        error.hidden = false;
        opts.onError?.(err);
    }
    const clearError = () => { error.hidden = true; };

    let lastTheirs = null;   // their newest yarn, for avatarOn: 'latest'
    let sending = [];        // yarns shown the moment you press send, until the server answers
    let tmp = 0;
    let unreadFrom = null;   // id of the first yarn that was unread when the thread opened
    let loadingEarlier = false;
    const toBottom = () => { list.scrollTop = list.scrollHeight; jump.hidden = true; };
    const nearBottom = () => list.scrollHeight - list.scrollTop - list.clientHeight < 80;

    // anchor: 'keep' = same scrollTop (things added below), 'prepend' = same distance from the bottom (things added above), 'end' = the newest yarn
    function render(anchor = 'keep') {
        lastTheirs = items.findLast((y) => y.kind !== 'SYSTEM' && y.senderId !== opts.meId) || null;
        const top = list.scrollTop, fromEnd = list.scrollHeight - list.scrollTop;
        list.replaceChildren(...[
            more ? h('button', { class: 'yarn-thread__more', type: 'button', text: 'Load earlier yarns', onclick: loadEarlier }) : null,
            ...items.concat(sending).flatMap((y) => (y.id === unreadFrom ? [h('p', { class: 'yarn-thread__new', text: 'New yarns' }), bubble(y)] : [bubble(y)])),
        ].filter(Boolean));   // a bare null would print as the text "null"
        if (anchor === 'end') toBottom();
        else if (anchor === 'prepend') list.scrollTop = list.scrollHeight - fromEnd;
        else list.scrollTop = top;
    }
    /** The first unread yarn is the Nth from the newest among other people's yarns, N being the unread count. */
    function firstUnread() {
        let n = opts.unread || 0;
        if (!n) return null;
        for (let i = items.length - 1; i >= 0; i--) {
            const y = items[i];
            if (y.kind === 'SYSTEM' || y.senderId === opts.meId) continue;
            if (--n === 0) return y.id;
        }
        return items.find((y) => y.kind !== 'SYSTEM' && y.senderId !== opts.meId)?.id ?? null;
    }
    function postCard(url) {
        const u = new URL(url, location.href);
        if (u.origin !== location.origin) return url;   // someone else's site stays plain text
        const id = u.searchParams.get('id') || u.searchParams.get('post');
        const card = h('a', { class: 'yarn-thread__post', href: u.pathname + u.search, 'aria-label': 'Open the post' }, h('strong', { text: 'Open the post' }));
        const fill = (p) => {
            const pic = p.media?.find((m) => m.kind !== 'VIDEO');
            const who = p.anonymous && !p.mine ? 'Anonymous' : (p.author?.fullName || p.author?.username || '');
            card.replaceChildren(...[
                !p.anonymous && p.author?.username && face(p.author.username, 'yarn-thread__post-face'),   // the author's picture, on the corner of the bubble
                pic && h('img', { class: 'yarn-thread__post-pic', src: `/api/media/${pic.id}`, alt: '', loading: 'lazy' }),
                h('span', { class: 'yarn-thread__post-body' }, h('strong', { text: p.title }), who && h('small', { text: who }), p.body && h('span', { class: 'yarn-thread__post-text', text: p.body }))].filter(Boolean));   // (replaceChildren would print an undefined)
        };
        if (seen.has(id)) fill(seen.get(id));
        else opts.postInfo?.(id).then((p) => { seen.set(id, p); if (card.isConnected) fill(p); }).catch(() => {});
        return card;
    }
    const SHARED = /^\S+ shared a post with you: "[\s\S]*"\s*$/;   // the app's own line before a shared post; the card says it already
    const withCards = (text) => text.split(POST_LINK).flatMap((part, i) => (i % 2 ? [postCard(part)] : SHARED.test(part) || !part.trim() ? [] : [part.replace(/\n+$/, '')]));

    function bubble(y) {
        if (y.kind === 'SYSTEM') return h('p', { class: 'yarn-thread__system', text: y.body });
        const mine = y.senderId === opts.meId;
        const msg = message(y, mine);
        if (mine || !opts.avatar) return msg;
        const shown = opts.avatarOn !== 'latest' || y === lastTheirs;
        return h('div', { class: 'yarn-thread__row' }, h('span', { class: 'yarn-thread__pic' }, shown ? opts.avatar(y.sender) : null), msg);
    }
    function message(y, mine) {
        return h('div', { class: `yarn-thread__msg ${mine ? 'yarn-thread__msg--mine' : 'yarn-thread__msg--theirs'}${y.sending ? ' yarn-thread__msg--sending' : ''}` },
            !mine && opts.showNames ? h('strong', { class: 'yarn-thread__name', text: y.sender }) : null,
            y.pinned ? h('span', { class: 'yarn-thread__pinned', text: 'PINNED' }) : null,
            h('span', { class: 'yarn-thread__text' }, ...withCards(y.body)),
            h('span', { class: 'yarn-thread__meta' },
                h('time', { class: 'yarn-thread__time', text: new Date(y.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }),
                opts.onPin && opts.canPin?.() !== false && !y.sending ? h('button', { class: 'yarn-thread__pin', type: 'button', text: y.pinned ? 'Unpin' : 'Pin', 'aria-label': y.pinned ? 'Unpin this yarn' : 'Pin this yarn', onclick: () => opts.onPin(y) }) : null,
                mine && y.receipt ? ticks(y.receipt) : null));
    }
    async function refresh() {
        if (dead) return;
        lastRefresh = Date.now();
        try {
            const page = await opts.load();
            const fresh = page.slice().reverse();
            const first = !items.length;
            const grew = fresh.length && (first || fresh[fresh.length - 1].id !== items[items.length - 1].id);
            if (first && !fresh.length) render();   // an empty chat: the placeholders give way to its own empty state
            if (grew) {
                // The newest page is merged in, so earlier pages already loaded stay put.
                const atEnd = nearBottom(), ids = new Set(fresh.map((y) => y.id));
                items = first ? fresh : items.filter((y) => !ids.has(y.id) && y.at < fresh[0].at).concat(fresh);
                if (first) { more = page.length >= PAGE; unreadFrom = firstUnread(); }
                render(!first && atEnd ? 'end' : 'keep');
                if (first) {
                    // open on the first unread yarn, or on the newest one when everything is read
                    const mark = list.querySelector('.yarn-thread__new');
                    if (mark) list.scrollTop = Math.max(0, mark.offsetTop - 12); else toBottom();
                } else if (!atEnd) jump.hidden = false;   // never yank a reader away from where they are
                if (document.visibilityState === 'visible') opts.onRead?.();
            } else if (fresh.some((y) => y.receipt && items.find((x) => x.id === y.id)?.receipt !== y.receipt)) {
                // Nothing new to read, only ticks moved: update them in place. No scroll, and no read mark (that would echo back and forth).
                const now = new Map(fresh.map((y) => [y.id, y.receipt]));
                items = items.map((y) => (now.has(y.id) ? { ...y, receipt: now.get(y.id) } : y));
                render('keep');
            }
            clearError();
            layout();
        } catch (err) { if (!items.length) list.replaceChildren(); fail(err); }   // no placeholders left pulsing under an error
    }
    async function loadEarlier() {
        if (loadingEarlier || !items.length) return;
        loadingEarlier = true;
        try {
            const page = await opts.load(items[0].at);
            items = page.slice().reverse().concat(items);
            more = page.length >= PAGE;
            render('prepend');   // the yarn you were reading stays exactly where it was
        } catch (err) { fail(err); }
        finally { loadingEarlier = false; }
    }
    async function submit() {
        const body = box.value.trim();
        if (!body || sendBtn.disabled) return;
        // it appears at once (dimmed); when the server answers it becomes the real yarn, and on a failure it goes back into the box
        const mine = { id: `sending-${++tmp}`, kind: 'YARN', body, senderId: opts.meId, sender: '', at: new Date().toISOString(), sending: true };
        sending = sending.concat(mine);
        box.value = ''; grow();
        render('end');
        sendBtn.disabled = true;
        try {
            const made = await opts.send(body);
            sending = sending.filter((y) => y !== mine);
            if (!items.some((y) => y.id === made.id)) items = items.concat(made);   // a refresh may have brought it already
            render('end'); clearError();
        } catch (err) {
            sending = sending.filter((y) => y !== mine);
            if (!box.value) { box.value = body; grow(); }
            render('keep'); fail(err);
        }
        finally { sendBtn.disabled = false; box.focus({ preventScroll: true }); }
    }
    // A taller box takes room from the list, not from the page; if you were at the newest yarn you stay there.
    function grow() {
        const atEnd = nearBottom();
        box.style.height = 'auto'; box.style.height = Math.min(box.scrollHeight, 120) + 'px';
        if (atEnd) toBottom();
    }

    // The thread fills exactly the visible space between where it starts and the bottom nav, so the composer never moves.
    // When the keyboard opens the visual viewport shrinks, the nav is covered, and the list gives up the room instead of the page scrolling.
    let tallest = 0, locked = false;
    function layout() {
        if (dead || !element.isConnected) return;
        if (!locked) { document.documentElement.style.overflow = 'hidden'; locked = true; }
        const vv = window.visualViewport, vh = vv ? vv.height : window.innerHeight, off = vv ? vv.offsetTop : 0;
        tallest = Math.max(tallest, vh);
        const keyboard = vh < tallest - 120;
        const atEnd = nearBottom();
        const room = (off + vh - element.getBoundingClientRect().top) / zoomOf() - 8;
        const bar = document.documentElement.dataset.nav === 'omni-wheel' ? '88px' : '8px';   // room kept for the round button; none without it
        element.style.height = `calc(${room}px - ${keyboard ? '0px' : `${bar} - env(safe-area-inset-bottom, 0px)`})`;
        if (atEnd) toBottom();
    }
    const relayout = () => requestAnimationFrame(layout);

    function setDisabledReason(text) {
        notice.textContent = text || ''; notice.hidden = !text; composer.hidden = !!text;
    }

    sendBtn.addEventListener('click', submit);
    box.addEventListener('input', grow);
    list.addEventListener('scroll', () => {
        if (list.scrollTop < 60 && more) loadEarlier();   // reaching the top fetches the next page by itself
        if (nearBottom()) jump.hidden = true;
    }, { passive: true });
    window.addEventListener('resize', relayout);
    window.visualViewport?.addEventListener('resize', relayout);
    window.visualViewport?.addEventListener('scroll', relayout);
    relayout();
    box.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); } });
    setDisabledReason(opts.disabledReason);

    // Signals do the real work. The poll is the net under them: fast when there is no live socket, slow (30s) when there is.
    const timer = setInterval(() => { if (!(opts.isLive?.() && Date.now() - lastRefresh < 30_000)) refresh(); }, opts.pollMs ?? 6000);
    const unsubscribe = opts.signals?.(refresh);
    // Looked at for real: a thread that loaded while the tab was hidden counts as read the moment the tab is shown.
    const onShown = () => { if (document.visibilityState === 'visible' && items.length && !dead) { opts.onRead?.(); refresh(); } };
    document.addEventListener('visibilitychange', onShown);
    list.replaceChildren(...skeletonBubbles());   // the chat's shape until its yarns arrive
    refresh();
    return { element, refresh, redraw: () => render('keep'), setDisabledReason, destroy() {
        dead = true; clearInterval(timer); unsubscribe?.(); document.removeEventListener('visibilitychange', onShown);
        window.removeEventListener('resize', relayout);
        window.visualViewport?.removeEventListener('resize', relayout);
        window.visualViewport?.removeEventListener('scroll', relayout);
        if (locked) document.documentElement.style.overflow = '';
    } };
}

// One tick = saved. Two = delivered. Two glowing green = read. The shape changes too, so the state never depends on colour alone.
const CHECK = 'M1 5.8 4.3 9 11.5 1.6', CHECK2 = 'M6.2 8.6 7.3 9.6 15 1.6';
const TICK_LABEL = { SENT: 'Sent', DELIVERED: 'Delivered', READ: 'Read' };
function ticks(state) {
    const el = h('span', { class: `yarn-thread__ticks is-${state.toLowerCase()}`, role: 'img', 'aria-label': TICK_LABEL[state] || state, title: TICK_LABEL[state] || state });
    el.innerHTML = `<svg viewBox="0 0 16 11" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${CHECK}"/>${state === 'SENT' ? '' : `<path d="${CHECK2}"/>`}</svg>`;   // static markup, no user text
    return el;
}

function h(tag, props = {}, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'text') el.textContent = v;
        else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
        else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids) if (kid != null && kid !== false) el.append(kid);
    return el;
}

function loadStylesOnce(href, componentName) {
    const existing = document.querySelector(`link[data-component="${componentName}"]`);
    if (existing) return existing.sheet ? Promise.resolve() : new Promise((r) => existing.addEventListener('load', r, { once: true }));
    return new Promise((resolve, reject) => {
        const link = document.createElement('link');
        link.rel = 'stylesheet'; link.href = href; link.dataset.component = componentName;
        link.onload = () => resolve(); link.onerror = () => reject(new Error(`Could not load ${href}`));
        document.head.appendChild(link);
    });
}
