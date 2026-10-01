// Report settings, a page of its own: stay anonymous or not. Kept in this tab; the report page sends it with the report.
import { mountSettingsNav } from '/js/services/settings-nav.js';
import { reportSet } from '/js/services/stash.js';
import { h } from '/js/services/dom.js';
import { currentUser } from '/js/services/api.js';

async function boot() {
    mountSettingsNav('/HTML-pages/report.html');
    if (!(await currentUser().catch(() => null))) return location.replace('/HTML-pages/login.html?next=' + encodeURIComponent(location.pathname));
    const s = { anonymous: false, ...(reportSet.read() || {}) };
    const box = h('input', { type: 'checkbox', checked: s.anonymous, onchange: () => { s.anonymous = box.checked; reportSet.write(s); } });
    document.getElementById('section').replaceChildren(h('section', { class: 'pst-card sp-glass' }, h('h2', { text: 'On the report' }),
        h('label', { class: 'pst-switch' }, box, h('span', {}, h('strong', { text: 'Stay anonymous' }),
            h('small', { text: 'The admin console shows "Anonymous" instead of your name. We still keep it on our side to stop abuse.' })))));
}
boot();
