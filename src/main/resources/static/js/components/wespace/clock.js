// The response clock: how long a flagged collaborator has to answer. The founder sets it (never under 48 hours); everyone else reads it.
import { wespaceClock } from '/js/services/api.js';
import { h, toast } from '/js/services/dom.js';

const MIN_CLOCK = 48;
const MAX_CLOCK = 8760;

export function clockRow(about, refresh) {
    const text = h('p', { class: 'sp-sub', text: `Response clock: ${about.responseClockHours} hours. A flagged collaborator has this long to answer before the others vote.` });
    if (about.role !== 'FOUNDER') return text;
    const hours = h('input', { class: 'sp-input', type: 'number', min: MIN_CLOCK, max: MAX_CLOCK, value: about.responseClockHours, 'aria-label': 'Response clock in hours' });
    const save = async () => { try { await wespaceClock(about.postId, Number(hours.value)); toast('Response clock updated.'); await refresh(); } catch (err) { toast(err.message); } };
    return h('div', {}, text, h('span', { class: 'spc-perms' }, hours, h('button', { class: 'sp-btn', type: 'button', text: 'Set clock', onclick: save })));
}
