// Sign-in page. The network call lives in js/services/api.js.
import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
import { loginUser, currentUser } from '/js/services/api.js';

const DEFAULT_NEXT = '/HTML-pages/gaze.html';
const WELCOME = '/HTML-pages/welcome.html';   // a brand-new account sees this once, before anything else

// Only same-site paths are allowed as a destination, so a crafted ?next= can't send people elsewhere.
function safeNext() {
    const next = new URLSearchParams(location.search).get('next') || '';
    return next.startsWith('/') && !next.startsWith('//') && !next.includes('\\') ? next : DEFAULT_NEXT;
}

const form = document.getElementById('login-form');
const error = document.getElementById('error');
const submit = document.getElementById('submit');
let countdown;

function show(text) {
    error.textContent = text; error.hidden = !text;
    if (text) error.scrollIntoView({ behavior: 'smooth', block: 'center' });   // the error may sit below the fold on a small screen
}

function lockout(seconds) {   // "too many attempts": count down, then let them try again
    clearInterval(countdown);
    let left = seconds;
    submit.disabled = true;
    const tick = () => {
        if (left <= 0) { clearInterval(countdown); submit.disabled = false; show(''); return; }
        show(`Too many attempts. Try again in ${left < 60 ? `${left}s` : `${Math.ceil(left / 60)} min`}.`);
        left -= 1;
    };
    tick();
    countdown = setInterval(tick, 1000);
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const identifier = form.identifier.value.trim();
    const password = form.password.value;
    if (!identifier || !password) return show('Enter your email or username and your password.');
    show('');
    submit.disabled = true;
    try {
        const me = await loginUser(identifier, password);
        location.replace(me?.needsWelcome ? WELCOME : safeNext());
        return;
    } catch (err) {
        if (err.retryAfterSeconds) return lockout(err.retryAfterSeconds);
        show(err.requiresVerification ? `${err.message} Finish verifying on the sign-up page.` : err.message);
    }
    submit.disabled = false;
});

(async () => {
    mountThemeSwitcher('#theme-slot', { inline: true });
    const me = await currentUser();
    if (me) location.replace(me.needsWelcome ? WELCOME : safeNext());   // already signed in
})();
