// Which navigation this account uses (Settings > Appearance and themes > Navigation preference). Import it on any page that mounts a bottom bar.
// It sets <html data-nav="..."> at once from the copy kept on this device, then asks the server (the pick follows the account across devices),
// and puts a Return button in the page header. css/global/global.css shows the wheel and the Return button according to data-nav.
import { currentUser, profileUpdate } from '/js/services/api.js';
import { NAV_PREFERENCES, DEFAULT_NAV_PREFERENCE } from '/js/services/nav-preference-source.js';

const KEY = 'collabo.navPreference';
const GAZE = '/HTML-pages/gaze.html';
const CHEVRON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg>';

const known = (id) => NAV_PREFERENCES.some((p) => p.id === id);
const cached = () => { try { return localStorage.getItem(KEY); } catch { return null; } };
const keep = (id) => { try { if (id) localStorage.setItem(KEY, id); else localStorage.removeItem(KEY); } catch { /* the pick just isn't remembered on this device */ } };

/** The id in use: the account's pick, else the default. */
export const navMode = () => (known(cached()) ? cached() : DEFAULT_NAV_PREFERENCE);
const apply = () => { document.documentElement.dataset.nav = navMode(); };

/** Pages with unsaved work (post, report) say how Return leaves: guard(go) runs go() to leave, or asks first. */
let leaveGuard = (go) => go();
export const guardReturn = (g) => { leaveGuard = g; };

/** Picks a navigation: applies it now, keeps it on this device, and saves it on the account. */
export async function saveNavPreference(id) {
    keep(id); apply();
    await profileUpdate({ navPreference: id });
}

async function sync() {
    const me = await currentUser().catch(() => null);
    if (!me) return;
    keep(known(me.navPreference) ? me.navPreference : null); apply();
}

const goBack = () => leaveGuard(() => { if (history.length > 1) history.back(); else location.href = GAZE; });

function returnButton() {
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'nav-return'; b.setAttribute('aria-label', 'Return'); b.title = 'Return';
    b.innerHTML = CHEVRON; b.addEventListener('click', goBack);
    return b;
}

// The top left of every header, except the Gaze (home has nothing to return to) and pages that already carry their own (#back).
function addReturn() {
    if (document.body.hasAttribute('data-home') || document.querySelector('#back, .nav-return')) return;
    const bar = document.querySelector('.tt-bar'), head = document.querySelector('.sp-header');
    if (bar) {
        let tabs = bar.querySelector('.tt-tabs');
        if (!tabs) { tabs = document.createElement('div'); tabs.className = 'tt-tabs'; const h1 = bar.querySelector('.tt-title'); if (h1) tabs.append(h1); bar.prepend(tabs); }
        tabs.prepend(returnButton());
    } else if (head) (head.querySelector('.yn-header__top') || head).prepend(returnButton());   // the Yarns header stacks its rows, so Return joins the top one
}

// The four-square menu beside the bell, on every header: a list of the main pages, since the wheel is not there to lead to them.
// Pages with no bell get one here too. Signed-in users only.
const GRID = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.6"/></svg>';

function menuButton() {
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'nav-gear nav-menu'; b.setAttribute('aria-label', 'Menu'); b.title = 'Menu'; b.innerHTML = GRID;
    b.addEventListener('click', () => import('/js/services/nav-menu.js').then((m) => m.openNavMenu(leaveGuard)));
    return b;
}

function bellSlot() {
    let slot = document.getElementById('bell-slot');
    if (slot) return slot;
    slot = document.createElement('span'); slot.id = 'bell-slot';
    const end = document.querySelector('.tt-end'), head = document.querySelector('.sp-header');
    const into = end || (head && (head.querySelector('.yn-header__top') || head));
    if (!into) return null;
    into.append(slot);
    import('/js/services/bell.js');   // reads #bell-slot as it loads, so the slot goes in first
    return slot;
}

async function addMenu() {
    if (document.querySelector('.nav-menu') || !(await currentUser().catch(() => null))) return;
    bellSlot()?.before(menuButton());
}

apply();
sync();
const ready = () => { addReturn(); addMenu(); };
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready); else ready();
