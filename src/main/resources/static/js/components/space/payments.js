// A space's payment records. COLLABO documents payments and never moves money: "claimed" and "confirmed" are statements by people.
// paymentsSection(space, { canLog, me, members }) returns a self-refreshing element. Text goes in through textContent only.
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { paymentsOf, paymentClaim, paymentConfirm, paymentCancel } from '/js/services/api.js';
import { face } from '/js/services/face.js';
import { h, toast, day } from '/js/services/dom.js';

const STATE = { CLAIMED: ['Claimed', 'sp-tag--brand'], CONFIRMED: ['Confirmed', 'sp-tag--ok'], CANCELLED: ['Cancelled', 'sp-tag--muted'] };

async function openClaim(space, members, me, onDone) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: 'Log a payment', html: '<h3 class="glass-blur-dialog__title">Log a payment</h3><form class="sp-form"></form>' });
    const to = h('select', { class: 'sp-input', 'aria-label': 'Paid to' }, ...members.filter((m) => m.person.username !== me.username).map((m) => h('option', { value: m.person.username, text: m.person.username })));
    const amount = h('input', { class: 'sp-input', type: 'number', min: '0.01', step: '0.01', required: true, placeholder: 'Amount', 'aria-label': 'Amount' });
    const currency = h('input', { class: 'sp-input', value: 'NGN', maxlength: 5, required: true, 'aria-label': 'Currency' });
    const note = h('input', { class: 'sp-input', maxlength: 200, placeholder: 'What was it for? (optional)', 'aria-label': 'Note' });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = panel.querySelector('form');
    form.append(h('label', {}, 'Paid to', to), h('label', {}, 'Amount', amount), h('label', {}, 'Currency', currency), note,
        h('p', { class: 'pc-hint', text: 'This records that you paid. It does not send money, and the room is held until the person confirms or you cancel.' }), err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit' }, 'Log payment')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try { await paymentClaim(space.id, { recipient: to.value, amount: amount.value, currency: currency.value, note: note.value }); close(); onDone(); }
        catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
}

export function paymentsSection(space, { canLog, me, members }) {
    const root = h('section', {});
    const head = h('h2', { class: 'sp-h2', text: 'Payment records' });
    const banner = h('p', { class: 'pc-error', style: 'margin-bottom:12px', hidden: true, text: 'A payment claim is open, so the room is held until it is confirmed or cancelled.' });
    const list = h('div', { 'aria-live': 'polite' });

    async function load() {
        let rows;
        try { rows = await paymentsOf(space.id); } catch (err) { list.replaceChildren(h('p', { class: 'pc-error', text: err.message })); return; }
        head.textContent = `Payment records · ${rows.length}`;
        banner.hidden = !rows.some((p) => p.state === 'CLAIMED');
        list.replaceChildren(...(rows.length ? rows.map(row) : [h('p', { class: 'pc-hint', text: 'No payments logged.' })]));
    }
    const act = (fn) => async () => { try { await fn(); await load(); } catch (err) { toast(err.message); } };

    function row(p) {
        const [label, tone] = STATE[p.state] || [p.state, 'sp-tag--muted'];
        return h('div', { class: 'sp-pay sp-glass' }, face(p.payer.username),
            h('div', { class: 'sp-pay__main' }, h('strong', { text: `${p.amount.toFixed(2)} ${p.currency}` }),
                h('div', { text: `${p.payer.username} → ${p.recipient.username} · ${day(p.createdAt)}${p.note ? ' · ' + p.note : ''}` })),
            h('span', { class: `sp-tag ${tone}`, text: label }),
            p.state === 'CLAIMED' && p.recipient.username === me.username && h('button', { class: 'sp-btn sp-btn--ok', type: 'button', text: 'Confirm I received it', onclick: act(() => paymentConfirm(space.id, p.id)) }),
            p.state === 'CLAIMED' && p.payer.username === me.username && h('button', { class: 'sp-btn sp-btn--danger', type: 'button', text: 'Cancel claim', onclick: act(() => paymentCancel(space.id, p.id)) }));
    }

    root.append(...[head,
        h('p', { class: 'sp-sub', text: 'Documented here, settled elsewhere. COLLABO never moves the money.' }),
        banner,
        canLog && h('div', { class: 'pc-actions' }, h('button', { class: 'sp-btn sp-btn--brand', type: 'button', text: '+ Log a payment', onclick: () => openClaim(space, members, me, load) })),
        list,
        h('div', { class: 'sp-fine', text: 'A confirmed entry proves both people said money moved. It is not evidence that it did. Until the recipient confirms, an entry reads "claimed".' })].filter(Boolean));
    load();
    return root;
}
