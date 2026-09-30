// Space settings: the name, the response clock and whether pleas are allowed. Every change is announced in the room.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { spaceSettingsUpdate } from '/js/services/api.js';
import { h } from '/js/services/dom.js';

export async function openSettings(space, onDone) {
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: 'Space settings', html: '<h3 class="glass-blur-dialog__title">Space settings</h3><form class="sp-form"></form>' });
    const name = h('input', { class: 'sp-input', maxlength: 80, value: space.name, required: true, 'aria-label': 'Space name' });
    const clock = h('input', { class: 'sp-input', type: 'number', min: 48, max: 8760, value: space.responseClockHours, required: true, 'aria-label': 'Response clock in hours' });
    const pleas = h('input', { type: 'checkbox', checked: space.pleasEnabled });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(h('label', {}, 'Name', name),
        h('label', {}, 'Response clock (hours, at least 48)', clock),
        h('label', { class: 'spc-check' }, pleas, ' Allow pleas (anyone can buy a quiet member 12 more hours)'),
        h('p', { class: 'pc-hint', text: 'Everyone in the room sees each change you make here.' }), err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Save')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await spaceSettingsUpdate(space.id, { name: name.value, responseClockHours: Number(clock.value), pleasEnabled: pleas.checked }); close(); onDone(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}
