// Account security rows for the Settings page (its "Account security" segment): change password, signed-in devices, delete account.
// securityRows(toast) returns the elements to append; each button opens its own dialog. Network calls live in js/services/api.js.
import { openGlassBlurDialog, glassBlurConfirm } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { sendPasswordCode, verifyPasswordCode, changePassword, mySessions, endSession, endOtherSessions, deleteAccount } from '/js/services/api.js';
import { h } from '/js/services/dom.js';
import '/js/components/password-toggle.js';

const SIGN_IN = '/HTML-pages/login.html';

const field = (label, name, type, auto) => h('label', {}, label, h('input', { class: 'sp-input', name, type, autocomplete: auto, required: true }));
const note = () => h('p', { class: 'pc-hint', role: 'alert', hidden: true, style: 'color:var(--sp-danger)' });
const say = (el, text) => { el.textContent = text; el.hidden = !text; };
const when = (iso) => new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });

async function dialog(title) {
    const { panel, close } = await openGlassBlurDialog({ size: 'sm', label: title, html: `<h3 class="glass-blur-dialog__title"></h3><div class="pf-settings"></div>` });
    panel.querySelector('.glass-blur-dialog__title').textContent = title;
    return { body: panel.querySelector('.pf-settings'), close };
}

// Change password has three steps: a code is emailed, you type it, then the form opens (the server won't take the form without the code).
async function openChangePassword(toast) {
    const { body, close } = await dialog('Change password');
    const wait = (ex) => ex.retryAfterSeconds ? `Too many attempts. Try again in ${Math.ceil(ex.retryAfterSeconds / 60)} min.` : ex.message;

    function askForCode() {
        const err = note(), send = h('button', { class: 'sp-btn sp-btn--brand', type: 'button', text: 'Email me a code' });
        send.addEventListener('click', async () => {
            say(err, ''); send.disabled = true;
            try { await sendPasswordCode(); enterCode(); }
            catch (ex) {
                if (ex.status === 429 && ex.retryAfterSeconds <= 60) { toast('A code was just sent. Use that one.'); return enterCode(); }   // the one-a-minute limit, not a lockout
                say(err, wait(ex)); send.disabled = false;
            }
        });
        body.replaceChildren(h('p', { class: 'pc-hint', text: "To make sure it's you, we email a 6-digit code to your address first." }), err, send);
    }

    function enterCode() {
        const err = note(), go = h('button', { class: 'sp-btn sp-btn--brand', type: 'submit', text: 'Continue' });
        const input = h('input', { class: 'sp-input', name: 'code', inputmode: 'numeric', autocomplete: 'one-time-code', maxlength: 6, required: true });
        const form = h('form', { class: 'sp-form', novalidate: true }, h('label', {}, 'Code from your email', input), err,
            h('p', { class: 'pc-hint', text: 'It expires in 10 minutes.' }), go,
            h('button', { class: 'sp-btn', type: 'button', text: 'Send a new code', onclick: askForCode }));
        form.addEventListener('submit', async (e) => {
            e.preventDefault(); say(err, ''); go.disabled = true;
            try { await verifyPasswordCode(input.value); enterPasswords(); }
            catch (ex) { say(err, wait(ex)); go.disabled = false; }
        });
        body.replaceChildren(form);
        input.focus();
    }

    function enterPasswords() {
        const err = note(), save = h('button', { class: 'sp-btn sp-btn--brand', type: 'submit', text: 'Change password' });
        const form = h('form', { class: 'sp-form', novalidate: true },
            field('Current password', 'current', 'password', 'current-password'), field('New password (8 or more characters)', 'password', 'password', 'new-password'),
            err, h('p', { class: 'pc-hint', text: 'Your other devices will be signed out.' }), save);
        form.addEventListener('submit', async (e) => {
            e.preventDefault(); say(err, ''); save.disabled = true;
            try { await changePassword(form.current.value, form.password.value); close(); toast('Password changed. Your other devices are signed out.'); }
            catch (ex) { say(err, wait(ex)); save.disabled = false; }
        });
        body.replaceChildren(form);
    }

    askForCode();
}

async function openDevices(toast) {
    const { body } = await dialog('Signed-in devices');
    async function draw() {
        let list;
        try { list = await mySessions(); } catch (ex) { return body.replaceChildren(h('p', { class: 'pc-hint', text: ex.message })); }
        const rows = list.map((s) => h('div', { class: 'sp-form' },
            h('strong', { text: s.current ? `${s.device} (this device)` : s.device }),
            h('span', { class: 'pc-hint', text: `Signed in ${when(s.createdAt)}. Last active ${when(s.lastActive)}.` }),
            !s.current && h('button', { class: 'sp-btn sp-btn--danger', type: 'button', text: 'Sign out', onclick: async () => {
                try { await endSession(s.id); toast('Signed out.'); } catch (ex) { toast(ex.message); }
                draw();
            } })));
        const others = h('button', { class: 'sp-btn', type: 'button', text: 'Sign out of all other devices', disabled: list.length < 2, onclick: async () => {
            try { await endOtherSessions(); toast('All other devices are signed out.'); } catch (ex) { toast(ex.message); }
            draw();
        } });
        body.replaceChildren(...rows, others);
    }
    await draw();
}

async function openDelete(toast) {
    const { body, close } = await dialog('Delete account');
    const err = note(), go = h('button', { class: 'sp-btn sp-btn--danger', type: 'submit', text: 'Delete my account' });
    const form = h('form', { class: 'sp-form', novalidate: true },
        h('p', { class: 'pc-hint', text: 'Your ideas, drafts, photo, links, follows and notifications are removed, and you are signed out everywhere. '
            + 'Your name is wiped from the rest (comments, teams, records show "deleted"). This cannot be undone.' }),
        field('Your password, to confirm', 'password', 'password', 'current-password'), err, go);
    form.addEventListener('submit', async (e) => {
        e.preventDefault(); say(err, '');
        if (!await glassBlurConfirm('Delete your account for good?', { danger: true, okText: 'Delete' })) return;
        go.disabled = true;
        try { await deleteAccount(form.password.value); close(); location.replace(SIGN_IN); }
        catch (ex) { say(err, ex.retryAfterSeconds ? `Too many attempts. Try again in ${Math.ceil(ex.retryAfterSeconds / 60)} min.` : ex.message); go.disabled = false; }
    });
    body.append(form);
}

export function securityRows(toast) {
    const row = (text, open, danger) => h('button', { class: `sp-btn${danger ? ' sp-btn--danger' : ''}`, type: 'button', text, onclick: () => open(toast) });
    return [row('Change password', openChangePassword), row('Signed-in devices', openDevices), row('Delete account', openDelete, true)];
}
