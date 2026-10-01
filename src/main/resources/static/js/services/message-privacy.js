// "Who can start a new message with me": one select that saves as soon as it changes. Used in the profile and the Yarns settings.
import { h } from '/js/services/dom.js';
import { profileGet, profileUpdate } from '/js/services/api.js';

const OPTIONS = [['EVERYONE', 'Everyone'], ['FOLLOWERS', 'People who follow me'], ['FOLLOWING', 'People I follow'], ['MUTUAL', 'Mutual follows']];

export async function messagePrivacyRow(username, toast) {
    const pick = h('select', { class: 'sp-input', id: 'msg-privacy', disabled: true }, ...OPTIONS.map(([v, t]) => h('option', { value: v, text: t })));
    const row = h('label', { for: 'msg-privacy' }, 'Who can message me', pick);
    try { pick.value = (await profileGet(username)).messagePrivacy || 'EVERYONE'; pick.disabled = false; } catch { /* stays disabled: could not load */ }
    pick.addEventListener('change', async () => {
        try { await profileUpdate({ messagePrivacy: pick.value }); toast?.('Saved.'); } catch (e) { toast?.(e.message); }
    });
    return row;
}
