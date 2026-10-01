// Pieces the review pages share: the TikTok-style tab row, the sort menu under the filter icon, the same sign-in/back handling.
import { h } from '/js/services/dom.js';

/** tabs: [[key, label, count]]. Draws into nav and calls onPick(key) when one is tapped. */
export function drawTabs(nav, tabs, current, onPick) {
    nav.replaceChildren(...tabs.map(([k, label, n]) => h('button', {
        class: 'cn-tab' + (k === current ? ' is-on' : ''), type: 'button', role: 'tab', 'aria-selected': String(k === current),
        onclick: () => onPick(k),
    }, h('strong', { text: String(n) }), h('span', { text: label }))));
}

/** A small menu under `anchor`. groups: [[{key,label}]]; checked(key) says which is on; onPick(key) runs on tap. Closes on any outside tap. */
export function openMenu(anchor, groups, checked, onPick) {
    document.querySelector('.rv-menu')?.remove();
    const menu = h('div', { class: 'rv-menu', role: 'menu' });
    groups.forEach((g, i) => {
        if (i) menu.append(h('hr'));
        g.forEach(({ key, label }) => menu.append(h('button', { type: 'button', role: 'menuitemradio', 'aria-checked': String(checked(key)), text: label, onclick: () => { onPick(key); menu.remove(); } })));
    });
    const r = anchor.getBoundingClientRect();
    menu.style.top = `${r.bottom + 6}px`; menu.style.right = `${Math.max(8, innerWidth - r.right)}px`;
    document.body.append(menu);
    setTimeout(() => addEventListener('click', () => menu.remove(), { once: true }), 0);
}

export const toLogin = () => location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname + location.search));
