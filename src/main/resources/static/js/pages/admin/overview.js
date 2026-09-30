// Overview: is the server healthy and configured for production, and how big is it. Every check that is not green says what to do.
import { h } from '/js/services/dom.js';
import { duration } from './ui.js';

/** The same checks feed the status strip and this page. state: ok | warn | bad. */
export function checks(s) {
    return [
        { key: 'database', label: 'Database', state: s.databaseOk ? 'ok' : 'bad', value: s.database,
            fix: s.databaseOk ? '' : 'The app cannot reach the database. On the VPS run: docker compose ps, then docker compose logs db --tail 50.' },
        { key: 'mail', label: 'Email', state: s.mailConfigured ? 'ok' : 'bad', value: s.mailConfigured ? 'Key set' : 'No key',
            fix: s.mailConfigured ? '' : 'RESEND_API_KEY is missing. People cannot get verification emails. Add it to /opt/collabo/.env, then: docker compose up -d --force-recreate backend.' },
        { key: 'cookies', label: 'Secure cookies', state: s.secureCookies ? 'ok' : 'warn', value: s.secureCookies ? 'On' : 'Off',
            fix: s.secureCookies ? '' : 'Expected while running on http://localhost. On the live site, set SESSION_COOKIE_SECURE=true in .env once it is served over HTTPS.' },
        { key: 'test', label: 'Test accounts', state: s.testAccounts ? 'warn' : 'ok', value: s.testAccounts ? 'On' : 'Off',
            fix: s.testAccounts ? 'ALLOW_TEST_ACCOUNTS is on, so registration skips email checks and verification. Turn it off in production.' : '' },
    ];
}

const TILES = [['users', 'Users'], ['verified', 'Verified'], ['premium', 'Premium'], ['moderators', 'Moderators'], ['spaces', 'Spaces'], ['posts', 'Posts']];
const ICON = { ok: '✓', warn: '!', bad: '✕' };

export function overview(s) {
    const list = checks(s);
    const attention = list.filter((c) => c.state !== 'ok').length;
    return h('div', { class: 'ad-view' },
        h('h2', { text: 'Overview' }),
        h('p', { class: 'ad-lead', text: attention === 0 ? 'Everything is configured.' : `${attention} thing${attention === 1 ? '' : 's'} to look at.` }),
        h('div', { class: 'ad-checks' }, ...list.map((c) => h('div', { class: `ad-check is-${c.state}` },
            h('span', { class: 'ad-check__icon', 'aria-hidden': 'true', text: ICON[c.state] }),
            h('div', {}, h('strong', { text: c.label }), h('span', { class: 'ad-check__value', text: c.value }), c.fix && h('p', { text: c.fix }))))),
        h('div', { class: 'ad-tiles' }, ...TILES.filter(([k]) => k in s.counts).map(([k, label]) => h('div', { class: 'ad-tile' },
            h('strong', { text: String(s.counts[k]) }), h('span', { text: label })))),
        h('p', { class: 'ad-foot', text: `Server up for ${duration(s.uptimeSeconds)}.` }));
}
