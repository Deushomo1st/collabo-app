// One case on the moderator desk: the brief, the Yarnspace read-only, the findings so far, and the form to report back.
// The only things a moderator can send are a finding, a recommendation (appeals) and screenshots.
import * as api from '/js/services/moderator-api.js';
import { h, toast } from '/js/services/dom.js';

const TIER = { MYSPACE: 'MySpace (a direct conversation)', WESPACE: 'WeSpace', WORKSPACE: 'Workspace' };
const MAX_SHOTS = 5, MAX_BYTES = 1024 * 1024;
const when = (iso) => new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

const yarnRow = (y) => h('div', { class: `md-yarn${y.system ? ' is-system' : ''}` },
    h('div', { class: 'md-yarn__who' }, h('span', { text: y.system ? 'system' : y.sender || '(deleted)' }), h('time', { text: when(y.createdAt) })),
    h('div', { class: 'md-yarn__body', text: y.body }));

function findingCard(f) {
    const shots = f.screenshots.length;
    return h('div', { class: 'md-finding' },
        h('small', { text: `${when(f.createdAt)}${f.recommendation ? ` · recommends: badge ${f.recommendation === 'DROPS' ? 'drops' : 'sticks'}` : ''}${shots ? ` · ${shots} screenshot${shots > 1 ? 's' : ''} sent` : ''}` }),
        h('p', { text: f.text }));
}

/** The form: text, optional recommendation (appeals), up to five screenshots previewed before sending. */
function reportForm(c, onSent) {
    const picked = [];   // File objects, in order
    const text = h('textarea', { maxlength: '2000', placeholder: 'What did you find? Say what happened, who was involved, and where in the conversation.' });
    const rec = c.investigation.kind === 'APPEAL' && h('label', {}, 'Your recommendation on the badge (the admin decides)',
        h('select', {}, h('option', { value: '', text: 'No recommendation' }), h('option', { value: 'STICKS', text: 'The badge should stay' }), h('option', { value: 'DROPS', text: 'The badge should drop' })));
    const shots = h('div', { class: 'md-shots' });
    const error = h('p', { class: 'md-error', role: 'alert' });
    const send = h('button', { type: 'submit', text: 'Send to the admin' });
    const file = h('input', { type: 'file', accept: 'image/png,image/jpeg', multiple: true });

    const draw = () => {
        shots.replaceChildren(...picked.map((f, i) => {
            const url = URL.createObjectURL(f);
            return h('figure', {}, h('img', { src: url, alt: f.name, onload: () => URL.revokeObjectURL(url) }), h('figcaption', { text: f.name }),
                h('button', { type: 'button', class: 'md-ghost', text: 'Remove', onclick: () => { picked.splice(i, 1); draw(); } }));
        }));
    };
    file.addEventListener('change', () => {
        error.textContent = '';
        for (const f of file.files) {
            if (picked.length >= MAX_SHOTS) { error.textContent = `At most ${MAX_SHOTS} screenshots per finding.`; break; }
            if (f.size > MAX_BYTES) { error.textContent = `${f.name} is over 1 MB. Crop it or save it smaller.`; continue; }
            picked.push(f);
        }
        file.value = ''; draw();
    });

    return h('form', {
        class: 'md-form',
        onsubmit: async (e) => {
            e.preventDefault();
            error.textContent = '';
            if (!text.value.trim()) return void (error.textContent = 'Write what you found first.');
            send.disabled = true;
            let finding;
            try {
                finding = await api.addFinding(c.investigation.id, text.value, rec ? rec.querySelector('select').value : '');
                for (const f of picked) await api.addScreenshot(c.investigation.id, finding.id, f);
            } catch (err) {
                send.disabled = false;
                // The finding may already be saved; say so, so it is not sent twice.
                error.textContent = finding ? `Your finding was sent, but a screenshot failed: ${err.message}. Reload the case to see what arrived.` : err.message;
                if (err.status === 401) return location.reload();   // the session ended: the reload lands on sign-in
                return;
            }
            toast('Sent to the admin.');
            onSent();
        },
    }, h('h3', { text: 'Report to the admin' }), text, rec, h('label', {}, 'Screenshots (PNG or JPEG, up to 5, 1 MB each)', file), shots, error, h('div', { class: 'md-actions' }, send));
}

export async function caseView(id, reload) {
    const c = await api.openCase(id);
    const i = c.investigation;
    const appeal = i.kind === 'APPEAL';
    return h('div', { class: 'md-case-inner', style: 'display:contents' },
        h('div', {}, h('h2', { text: i.title || 'A conversation' }),
            h('p', { class: 'md-meta', text: `${appeal ? 'Termination appeal' : TIER[i.tier] || 'Yarnspace'} · opened ${when(i.createdAt)} · by ${i.reporter}` })),
        h('div', { class: 'md-brief' }, h('b', { text: appeal ? 'The appeal' : 'The report' }), h('span', { text: i.reason })),
        h('p', { class: 'md-note', text: appeal
            ? 'Read-only. You can see only the room from a day before the removal to an hour after it.'
            : 'Read-only. You can see this Yarnspace only while the case is open, and nothing else on the platform.' }),
        h('p', { class: 'md-meta', text: `In this Yarnspace: ${c.members.join(', ') || 'nobody left'}` }),
        h('div', { class: 'md-transcript', tabindex: '0', 'aria-label': 'Yarnspace transcript' },
            c.yarns.length ? c.yarns.map(yarnRow) : h('p', { class: 'md-empty', text: 'No yarns in this window.' })),
        c.findings.length ? h('div', { class: 'md-findings' }, h('h3', { text: 'Sent so far' }), c.findings.map(findingCard)) : null,
        reportForm(c, reload));
}
