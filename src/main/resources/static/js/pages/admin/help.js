// Help: the admin guide (docs/COLLABO-Admin-Guide.pdf) where you need it. Commands copy with one click and use this page's own host.
import { h } from '/js/services/dom.js';
import { copy } from './ui.js';

const host = location.hostname && location.hostname !== 'localhost' ? location.hostname : '<your-vps-ip>';

const cmd = (text) => h('div', { class: 'ad-cmd' }, h('code', { text }), h('button', { class: 'ad-btn ad-btn--small', type: 'button', text: 'Copy', onclick: () => copy(text) }));
const card = (title, ...kids) => h('section', { class: 'ad-card' }, h('h3', { text: title }), ...kids);
const p = (text) => h('p', { text });

const SYMPTOMS = [
    ['Wrong admin key at the lock screen', 'ADMIN_KEY does not match. Check /opt/collabo/.env on the VPS and paste it again.'],
    ['Admin key not configured', 'ADMIN_KEY is missing from .env. Add it, then: docker compose up -d --force-recreate backend'],
    ['ssh: Could not resolve hostname', 'Missing the @ between user and IP: deus@<ip>'],
    ['bind [127.0.0.1]:8081: Permission denied', 'That port is busy on your laptop. Use another local port, for example -L 19091:...'],
    ['channel: open failed: Connection refused', 'The tunnel works but Adminer is not running: docker compose up -d adminer'],
    ['The tunnel window looks frozen', 'It is working. Leave it open.'],
    ['Backend will not start after a deploy', 'Check .env first. RESEND_API_KEY, ADMIN_KEY and POSTGRES_PASSWORD must all be present.'],
    ['Nobody receives verification emails', 'The Email check on Overview is red. Fix RESEND_API_KEY.'],
];

export function helpView() {
    return h('div', { class: 'ad-view' },
        h('h2', { text: 'Help' }),
        h('p', { class: 'ad-lead', text: 'The operations guide, short. The full version is docs/COLLABO-Admin-Guide.pdf in the repo.' }),
        h('div', { class: 'ad-cards' },
            card('Raw database access (Adminer)',
                p('Adminer has no guardrails, so it is for edits this console does not do. It is only reachable through an SSH tunnel.'),
                cmd(`ssh -N -L 18081:127.0.0.1:8081 deus@${host}`),
                p('The window then looks frozen. That means it is working. Open http://localhost:18081 and sign in with System PostgreSQL, Server db, Username collabo_user, Database collabo. The password is POSTGRES_PASSWORD in /opt/collabo/.env.')),
            card('Deploying',
                p('Merging to main runs the pipeline: build the image, push it, recreate the backend on the VPS. Branches only run tests.'),
                cmd('git push origin main'),
                p('The pipeline recreates only the backend. A new compose service (like adminer) needs one manual start on the VPS.')),
            card('On the VPS',
                cmd(`ssh deus@${host}`), cmd('cd /opt/collabo && docker ps'), cmd('docker compose logs backend --tail 50'), cmd('docker compose up -d --force-recreate backend')),
            card('What the Overview checks mean',
                p('Database: the app can run a query. Email: RESEND_API_KEY is set. Secure cookies: SESSION_COOKIE_SECURE is true, which the live HTTPS site needs. Test accounts: ALLOW_TEST_ACCOUNTS is off, which production needs.'))),
        card('Symptom, then fix', h('div', { class: 'ad-scroll' }, h('table', { class: 'ad-table' },
            h('thead', {}, h('tr', {}, h('th', { text: 'Symptom' }), h('th', { text: 'Fix' }))),
            h('tbody', {}, ...SYMPTOMS.map(([a, b]) => h('tr', {}, h('td', { text: a }), h('td', { text: b }))))))));
}
