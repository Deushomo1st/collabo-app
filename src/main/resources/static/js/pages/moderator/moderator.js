// Moderator desk shell: sign-in, the list of your cases, hash routing (#case/<id>), and your own password.
// Yarnspaces only: there is no other screen on purpose.
import * as api from '/js/services/moderator-api.js';
import { h, toast } from '/js/services/dom.js';
import { caseView } from './case.js';

const $ = (id) => document.getElementById(id);
const caseId = () => (location.hash.startsWith('#case/') ? location.hash.slice(6) : null);

function showSignin(message = '') {
    $('desk').hidden = true;
    $('signin').hidden = false;
    $('signin-error').textContent = message;
    $('signin-password').value = '';
    $('signin-email').focus();
}

async function showDesk(moderator) {
    $('signin').hidden = true;
    $('desk').hidden = false;
    $('who').textContent = moderator.name;
    await refresh();
}

// A 401 anywhere means the session ended (signed out elsewhere, or the admin deactivated this account).
const expired = (e) => { if (e.status === 401) { showSignin('Your session ended. Sign in again.'); return true; } return false; };

let list = [];

async function refresh() {
    try { list = await api.cases(); } catch (e) { if (!expired(e)) $('cases').replaceChildren(h('p', { class: 'md-empty', text: e.message })); return; }
    drawList();
    await drawCase();
}

function drawList() {
    const open = caseId();
    $('cases').replaceChildren(...(list.length ? list.map((c) => h('button', { type: 'button', class: `md-row${c.id === open ? ' is-on' : ''}`, onclick: () => { location.hash = `case/${c.id}`; } },
        h('span', {}, h('span', { class: `md-tag is-${c.kind === 'APPEAL' ? 'appeal' : c.status === 'REPORTED' ? 'reported' : ''}`.trim(), text: c.kind === 'APPEAL' ? 'Appeal' : c.status === 'REPORTED' ? 'Reported' : 'New' }), c.title || 'A conversation'),
        h('small', { text: `Assigned ${new Date(c.assignedAt).toLocaleDateString()}` })))
        : [h('p', { class: 'md-empty', text: 'Nothing assigned to you. The admin will assign a case when one needs a moderator.' })]));
}

async function drawCase() {
    const id = caseId(), pane = $('case');
    if (!id) return void pane.replaceChildren(h('p', { class: 'md-empty', text: list.length ? 'Pick a case to read its Yarnspace.' : '' }));
    pane.replaceChildren(h('p', { class: 'md-empty', text: 'Loading…' }));
    try {
        const node = await caseView(id, refresh);
        if (caseId() === id) pane.replaceChildren(node);   // you may have moved on while this loaded
    } catch (e) {
        if (expired(e)) return;
        if (caseId() === id) pane.replaceChildren(h('p', { class: 'md-empty', text: e.status === 404 ? 'This case is closed or no longer yours.' : e.message }));
    }
}

$('signin-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try { await showDesk(await api.login($('signin-email').value.trim(), $('signin-password').value)); }
    catch (err) { showSignin(err.status === 429 ? 'Too many attempts. Wait a little and try again.' : err.status === 401 ? 'Wrong email or password, or the account is deactivated.' : err.message); }
});
$('signout').addEventListener('click', async () => { try { await api.logout(); } catch { /* the page is signing out anyway */ } location.hash = ''; showSignin(); });
window.addEventListener('hashchange', () => { if (!$('desk').hidden) { drawList(); drawCase(); } });

const dialog = $('password-dialog');
$('password-btn').addEventListener('click', () => { $('password-form').reset(); $('pw-error').textContent = ''; dialog.showModal(); });
$('pw-cancel').addEventListener('click', () => dialog.close());
$('password-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try { await api.changePassword($('pw-current').value, $('pw-new').value); dialog.close(); toast('Password changed.'); }
    catch (err) { if (!expired(err)) $('pw-error').textContent = err.message; }
});

api.me().then((m) => (m ? showDesk(m) : showSignin())).catch((e) => showSignin(e.message));
