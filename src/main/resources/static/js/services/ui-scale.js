// How big everything is: text, icons and spacing together, one number on <html> (--ui-scale, which global.css turns into zoom).
// Two things make it: the size you pick in Settings > Appearance and themes (kept on this device, a phone and a laptop want different ones),
// (and it is saved on the account, so the same size follows you to every device), and a gentle shrink on narrow screens, so nothing is cut off before you have touched anything. Import it once per page; it applies at once.
const KEY = 'collabo.uiScale';
export const SCALE = { min: 0.8, max: 1.3, step: 0.05 };

/** The size you picked (1 = as designed). */
export function uiScale() {
    try { const v = parseFloat(localStorage.getItem(KEY)); return v >= SCALE.min && v <= SCALE.max ? v : 1; } catch { return 1; }
}
/** The shrink a narrow screen asks for on its own: below 390px wide it follows the width, never under 85%. */
export const narrowFactor = () => Math.max(0.85, Math.min(1, innerWidth / 390));
/** What is applied now: your size times the narrow-screen shrink. */
export const appliedScale = () => uiScale() * narrowFactor();

/** The zoom in force. getBoundingClientRect() and the pointer are in screen pixels, CSS lengths are in zoomed ones: divide a measurement by this before using it as a length. */
export const zoomOf = () => parseFloat(getComputedStyle(document.documentElement).zoom) || 1;

export function applyUiScale() { document.documentElement.style.setProperty('--ui-scale', appliedScale().toFixed(3)); }

/** Keeps a new size on this device and applies it to the page you are on. */
export function saveUiScale(v) {
    try { if (v === 1) localStorage.removeItem(KEY); else localStorage.setItem(KEY, String(v)); } catch { /* the size just isn't remembered */ }
    applyUiScale();
    dispatchEvent(new Event('resize'));   // what sizes itself from the window (the chat) measures again
}

/** The size the account has (percent, from the server): kept on this device and applied, so every device you sign in on looks the same. */
export function adoptUiScale(percent) {
    const v = percent >= SCALE.min * 100 && percent <= SCALE.max * 100 ? percent / 100 : 1;   // none saved yet: as designed
    if (v === uiScale()) return;
    try { if (v === 1) localStorage.removeItem(KEY); else localStorage.setItem(KEY, String(v)); } catch { /* applied for this visit only */ }
    applyUiScale();
}

applyUiScale();
addEventListener('resize', applyUiScale);
