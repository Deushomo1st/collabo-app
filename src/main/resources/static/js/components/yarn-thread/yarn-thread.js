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
//   });
//   host.append(t.element);  ...  t.destroy();      // destroy() stops the polling

const CSS_HREF = '/js/components/yarn-thread/yarn-thread.css';
const PAGE = 50;

export function preloadYarnThread() {
    return loadStylesOnce(CSS_HREF, 'yarn-thread');
}

// opts: { meId, showNames, load(before), send(body), onRead(), signals(refresh), isLive(), pollMs = 6000, disabledReason, onError(err) }
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
    const element = h('section', { class: 'yarn-thread' }, list, error, notice, composer);

    function fail(err) {
        error.textContent = err?.message || 'Something went wrong.';
        error.hidden = false;
        opts.onError?.(err);
    }
    const clearError = () => { error.hidden = true; };

    function render(keepScroll) {
        const before = document.documentElement.scrollHeight - window.scrollY;
        list.replaceChildren(...[
            more ? h('button', { class: 'yarn-thread__more', type: 'button', text: 'Load earlier yarns', onclick: loadEarlier }) : null,
            ...items.map(bubble),
        ].filter(Boolean));   // a bare null would print as the text "null"
        if (keepScroll) window.scrollTo({ top: document.documentElement.scrollHeight - before, behavior: 'instant' });
    }
    function bubble(y) {
        if (y.kind === 'SYSTEM') return h('p', { class: 'yarn-thread__system', text: y.body });
        const mine = y.senderId === opts.meId;
        return h('div', { class: `yarn-thread__msg ${mine ? 'yarn-thread__msg--mine' : 'yarn-thread__msg--theirs'}` },
            !mine && opts.showNames ? h('strong', { class: 'yarn-thread__name', text: y.sender }) : null,
            h('span', { class: 'yarn-thread__text', text: y.body }),
            h('span', { class: 'yarn-thread__meta' },
                h('time', { class: 'yarn-thread__time', text: new Date(y.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }),
                mine && y.receipt ? ticks(y.receipt) : null));
    }
    const nearBottom = () => document.documentElement.scrollHeight - window.scrollY - window.innerHeight < 160;

    async function refresh() {
        if (dead) return;
        lastRefresh = Date.now();
        try {
            const page = await opts.load();
            const fresh = page.slice().reverse();
            const grew = fresh.length && (!items.length || fresh[fresh.length - 1].id !== items[items.length - 1].id);
            const stick = !items.length || nearBottom();
            if (grew || !items.length) {
                items = fresh; more = page.length >= PAGE;
                render(false);
                if (stick) window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'instant' });
                if (document.visibilityState === 'visible') opts.onRead?.();
            } else if (fresh.some((y) => y.receipt && items.find((x) => x.id === y.id)?.receipt !== y.receipt)) {
                // Nothing new to read, only ticks moved: update them in place. No scroll, and no read mark (that would echo back and forth).
                const now = new Map(fresh.map((y) => [y.id, y.receipt]));
                items = items.map((y) => (now.has(y.id) ? { ...y, receipt: now.get(y.id) } : y));
                render(true);
            }
            clearError();
        } catch (err) { fail(err); }
    }
    async function loadEarlier() {
        try {
            const page = await opts.load(items[0].at);
            items = page.slice().reverse().concat(items);
            more = page.length >= PAGE;
            render(true);
        } catch (err) { fail(err); }
    }
    async function submit() {
        const body = box.value.trim();
        if (!body || sendBtn.disabled) return;
        sendBtn.disabled = true;
        try {
            const made = await opts.send(body);
            box.value = ''; grow();
            items = items.concat(made);
            render(false); clearError();
            window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'instant' });
        } catch (err) { fail(err); }
        finally { sendBtn.disabled = false; box.focus(); }
    }
    function grow() { box.style.height = 'auto'; box.style.height = Math.min(box.scrollHeight, 120) + 'px'; }

    function setDisabledReason(text) {
        notice.textContent = text || ''; notice.hidden = !text; composer.hidden = !!text;
    }

    sendBtn.addEventListener('click', submit);
    box.addEventListener('input', grow);
    box.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); } });
    setDisabledReason(opts.disabledReason);

    // Signals do the real work. The poll is the net under them: fast when there is no live socket, slow (30s) when there is.
    const timer = setInterval(() => { if (!(opts.isLive?.() && Date.now() - lastRefresh < 30_000)) refresh(); }, opts.pollMs ?? 6000);
    const unsubscribe = opts.signals?.(refresh);
    // Looked at for real: a thread that loaded while the tab was hidden counts as read the moment the tab is shown.
    const onShown = () => { if (document.visibilityState === 'visible' && items.length && !dead) { opts.onRead?.(); refresh(); } };
    document.addEventListener('visibilitychange', onShown);
    refresh();
    return { element, refresh, setDisabledReason, destroy() { dead = true; clearInterval(timer); unsubscribe?.(); document.removeEventListener('visibilitychange', onShown); } };
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
