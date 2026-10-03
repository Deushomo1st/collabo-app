// The founder's review of a post's applicants, on a page of its own. Reached as applicants.html?post=<post id>.
// Tabs: Pending, Shortlisted, Accepted, Declined. The funnel icon sorts. Forming the space is a step of its own at the top.
import '/js/services/live.js';
import { mountMainNav } from '/js/services/main-nav.js';
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';
import { currentUser, postGet, applicationStack, applicationDecide, applicationReact, spaceForm } from '/js/services/api.js';
import { face } from '/js/services/face.js';
import { h, toast, day, profileHref } from '/js/services/dom.js';
import { drawTabs, openMenu, toLogin } from '/js/services/review-ui.js';

const postId = new URLSearchParams(location.search).get('post');
const $tabs = document.getElementById('tabs'), $list = document.getElementById('list'), $sum = document.getElementById('summary'), $filter = document.getElementById('filter');
const TABS = [['SUBMITTED', 'Pending'], ['SHORTLISTED', 'Shortlisted'], ['ACCEPTED', 'Accepted'], ['DECLINED', 'Declined']];
const SORTS = [['recent', 'Newest first'], ['oldest', 'Oldest first'], ['longest', 'Longest statement']];
const words = (s) => (s.trim() ? s.trim().split(/\s+/).length : 0);
let post = null, rows = [], tab = 'SUBMITTED', sort = 'recent';

const say = (text) => $list.replaceChildren(h('p', { class: 'rv-empty', text }));

function visible() {
    const mine = rows.filter((r) => r.state === tab);
    if (sort === 'oldest') return mine.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    if (sort === 'longest') return mine.sort((a, b) => words(b.statement) - words(a.statement));
    return mine.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

function draw() {
    const n = (s) => rows.filter((r) => r.state === s).length;
    drawTabs($tabs, TABS.map(([k, label]) => [k, label, n(k)]), tab, (k) => { tab = k; draw(); });
    const accepted = n('ACCEPTED');
    $sum.replaceChildren(
        h('span', {}, h('strong', { text: post.title }), ` · ${rows.length} applied`),
        post.status === 'formed'
            ? h('a', { class: 'pc-btn pc-btn--brand', href: `/HTML-pages/space.html?post=${post.id}`, text: 'Open space' })
            : h('button', { class: 'pc-btn pc-btn--brand', type: 'button', disabled: !accepted, title: accepted ? '' : 'Accept at least one applicant first', text: 'Form space', onclick: openForm }));
    const shown = visible();
    $list.replaceChildren(...(shown.length ? shown.map(card) : [h('p', { class: 'rv-empty', text: `Nobody is ${TABS.find(([k]) => k === tab)[1].toLowerCase()} yet.` })]));
}

function card(a) {
    const who = a.applicant;
    const decide = (d, label, cls = '') => h('button', { class: `pc-btn ${cls}`, type: 'button', text: label, onclick: async () => {
        try { const next = await applicationDecide(a.id, d); rows = rows.map((r) => (r.id === a.id ? next : r)); draw(); }
        catch (err) { toast(err.message); }
    } });
    const locked = post.status === 'formed' && a.state === 'ACCEPTED';
    const closed = a.state === 'DECLINED' || locked;
    // tapping your own reaction again takes it back; the server may also move the application (a lost majority puts it back on the shortlist)
    const react = (kind, label) => h('button', { class: `pc-btn${a.myReaction === kind ? ' pc-btn--brand' : ''}`, type: 'button', text: label, onclick: async () => {
        try { const next = await applicationReact(a.id, a.myReaction === kind ? 'NONE' : kind); rows = rows.map((r) => (r.id === a.id ? next : r)); draw(); }
        catch (err) { toast(err.message); }
    } });
    const names = (list) => list.map((p) => p.username).join(', ');
    return h('div', { class: 'rv-card' },
        h('a', { class: 'rv-pic', href: profileHref(who.username), 'aria-label': `${who.username}'s profile` }, face(who.username, 'cn-face')),
        h('div', { class: 'rv-head' },
            h('a', { class: 'rv-name', href: profileHref(who.username), text: who.fullName || who.username }),
            who.fullName && h('span', { class: 'rv-at', text: `@${who.username}` }),
            who.preferredTitle && h('span', { class: 'sp-tag sp-tag--brand', text: who.preferredTitle }),
            h('time', { class: 'rv-time', datetime: a.createdAt, text: day(a.createdAt) })),
        h('p', { class: 'rv-text', text: a.statement }),
        (a.agree.length > 0 || a.disagree.length > 0) && h('p', { class: 'rv-votes pc-hint' },
            a.agree.length > 0 && `Agree: ${names(a.agree)}`, a.agree.length > 0 && a.disagree.length > 0 && ' · ', a.disagree.length > 0 && `Disagree: ${names(a.disagree)}`),
        !closed && h('div', { class: 'rv-acts' }, react('AGREE', 'Agree'), react('DISAGREE', 'Disagree')),
        !locked && h('div', { class: 'rv-acts' },
            a.state !== 'ACCEPTED' && decide('ACCEPT', 'Accept', 'pc-btn--brand'),
            a.state !== 'SHORTLISTED' && decide('SHORTLIST', 'Shortlist'),
            a.state !== 'DECLINED' && decide('DECLINE', 'Decline'),
            h('a', { class: 'pc-btn', href: profileHref(who.username), text: 'Credentials' })));
}

/** Forming is its own step: name, response clock and pleas, then it is done. */
async function openForm() {
    const { panel, close } = await openGlassBlurDialog({ size: 'md', label: 'Form space', html: '<h3 class="glass-blur-dialog__title">Form the space</h3><div class="ap-body"></div>' });
    const name = h('input', { class: 'sp-input', maxlength: 80, placeholder: post.title, 'aria-label': 'Space name' });
    const clock = h('input', { class: 'sp-input', type: 'number', min: 48, max: 8760, placeholder: '72', 'aria-label': 'Response clock in hours' });
    const pleas = h('input', { type: 'checkbox', checked: true });
    const err = h('p', { class: 'pc-error', hidden: true });
    const form = h('form', { class: 'sp-form' },
        h('label', { class: 'pc-hint' }, 'Space name', name),
        h('label', { class: 'pc-hint' }, 'Response clock, in hours (48 or more, 72 if empty)', clock),
        h('label', { class: 'gz-filter' }, pleas, ' Allow pleas (others may buy a quiet member 12 more hours)'),
        h('p', { class: 'pc-hint', text: 'Forming closes the post to new applications and tells everyone you accepted.' }), err,
        h('div', { class: 'glass-blur-dialog__actions' }, h('button', { class: 'glass-blur-dialog__btn', type: 'submit', text: 'Form space' })));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            const s = await spaceForm(post.id, { name: name.value, responseClockHours: clock.value ? Number(clock.value) : undefined, pleasEnabled: pleas.checked });
            close(); location.href = `/HTML-pages/space.html?id=${s.id}`;
        } catch (ex) { err.textContent = ex.message; err.hidden = false; }
    });
    panel.querySelector('.ap-body').append(form);
}

$filter?.addEventListener('click', () => openMenu($filter, [SORTS.map(([key, label]) => ({ key, label }))], (k) => k === sort, (k) => { sort = k; draw(); }));
document.getElementById('back').addEventListener('click', (e) => { if (history.length > 1 && document.referrer.startsWith(location.origin)) { e.preventDefault(); history.back(); } });

(async () => {
    if (!postId) return say('Open this from one of your posts.');
    try {
        const me = await currentUser();
        if (!me) return toLogin();
        mountMainNav(me.username, { onGaze: () => { location.href = '/HTML-pages/gaze.html'; } });
        [post, rows] = await Promise.all([postGet(postId), applicationStack(postId, 'recent', '')]);
        tab = TABS.find(([k]) => rows.some((r) => r.state === k))?.[0] || tab;   // open on the first tab that has someone in it
        draw();
    } catch (err) {
        if (err.status === 401) return toLogin();
        say(err.status === 404 ? 'That post is gone, or it is not yours to review.' : (err.message || 'Could not load the applicants.'));
    }
})();
