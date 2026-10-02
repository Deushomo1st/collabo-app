// Tiny DOM helpers shared by the newer pages. Text goes in through textContent only, never innerHTML.
export function h(tag, props = {}, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'text') el.textContent = v;
        else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
        else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) if (kid != null && kid !== false) el.append(kid);
    return el;
}

export function toast(text) {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = text; el.classList.add('is-on');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('is-on'), 2600);
}

export const day = (iso) => new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
/** Elapsed time for comments: "just now", "12m ago", "3h ago", then "2 days ago". */
export function ago(iso) {
    const mins = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000));
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    if (mins < 1440) return `${Math.floor(mins / 60)}h ago`;
    const d = Math.floor(mins / 1440);
    return `${d} day${d === 1 ? '' : 's'} ago`;
}
/** A post's age: minutes or hours on the same day, "N days ago" within the week, then the date. */
export function since(iso) {
    const start = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const days = Math.round((start(new Date()) - start(new Date(iso))) / 864e5);
    if (days <= 0) return ago(iso);
    return days < 7 ? `${days} day${days === 1 ? '' : 's'} ago` : day(iso);
}
export const profileHref = (name) => `/HTML-pages/profile.html?u=${encodeURIComponent(name)}`;
