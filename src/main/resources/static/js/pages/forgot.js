// Forgot password: ask for a code by email, then set a new password with it. The network calls live in js/services/api.js.
import { forgotPassword, resetPassword } from '/js/services/api.js';
import '/js/components/password-toggle.js';

const $ = (id) => document.getElementById(id);
const ask = $('ask-form'), reset = $('reset-form');
let email = '';

function say(el, text) { el.textContent = text; el.hidden = !text; }
const wait = (s) => `Too many attempts. Try again in ${s < 60 ? `${s}s` : `${Math.ceil(s / 60)} min`}.`;

async function sendCode(button, errorEl) {
    say(errorEl, ''); button.disabled = true;
    try { await forgotPassword(email); return true; }
    catch (err) { say(errorEl, err.retryAfterSeconds ? wait(err.retryAfterSeconds) : err.message); return false; }
    finally { button.disabled = false; }
}

ask.addEventListener('submit', async (e) => {
    e.preventDefault();
    email = ask.email.value.trim();
    if (!email) return say($('ask-error'), 'Enter the email you signed up with.');
    if (!await sendCode($('ask-submit'), $('ask-error'))) return;
    ask.hidden = true; reset.hidden = false;
    $('intro').textContent = `If ${email} has an account, a code is on its way. It works for 10 minutes.`;   // the same words whether it has one or not
    reset.code.focus();
});

$('again').addEventListener('click', async () => {
    if (await sendCode($('again'), $('reset-error'))) say($('reset-error'), 'A new code is on its way.');
});

reset.addEventListener('submit', async (e) => {
    e.preventDefault();
    const code = reset.code.value.trim(), password = reset.password.value;
    if (!/^\d{6}$/.test(code)) return say($('reset-error'), 'The code is 6 digits.');
    if (password.length < 8) return say($('reset-error'), 'Use at least 8 characters.');
    say($('reset-error'), ''); $('reset-submit').disabled = true;
    try {
        await resetPassword(email, code, password);
        reset.hidden = true;
        $('intro').textContent = 'Your password is changed and every device was signed out. Sign in with the new one.';
        document.querySelector('.lg-foot a').textContent = 'Sign in';
    } catch (err) {
        say($('reset-error'), err.retryAfterSeconds ? wait(err.retryAfterSeconds) : err.message);
        $('reset-submit').disabled = false;
    }
});

