// Reports tab of the admin panel: a moderator works the appeal queue here (open / decided), reads the room around the
// removal and decides the badge. It runs on the moderator's own sign-in (session cookie + CSRF), not on the admin key.
// Loaded before the panel's inline script, which calls AdminReports.load() / .count() / .signOut(). Text goes in via textContent only.
(function () {
    'use strict';

    var state = { status: 'open', selected: null };
    var $ = function (id) { return document.getElementById(id); };

    function el(tag, cls, text) {
        var n = document.createElement(tag);
        if (cls) n.className = cls;
        if (text !== undefined) n.textContent = text;
        return n;
    }
    function add(parent) { for (var i = 1; i < arguments.length; i++) if (arguments[i]) parent.appendChild(arguments[i]); return parent; }
    function when(iso) { var d = new Date(iso); return isNaN(d.getTime()) ? '' : d.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }); }
    function say(msg, isErr) { if (window.adminToast) window.adminToast(msg, isErr); }

    function csrf() {
        var m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
        return m ? decodeURIComponent(m[1]) : '';
    }

    async function call(path, opts) {
        opts = opts || {};
        var write = !!opts.method;
        if (write && !csrf()) await fetch('/api/auth/csrf');
        var headers = {};
        if (opts.body) headers['Content-Type'] = 'application/json';
        if (write) headers['X-XSRF-TOKEN'] = csrf();
        var res = await fetch(path, { method: opts.method || 'GET', headers: headers, body: opts.body });
        var data = res.status === 204 ? null : await res.json().catch(function () { return {}; });
        if (!res.ok) {
            var err = new Error((data && data.message) || 'HTTP ' + res.status);
            err.status = res.status;
            throw err;
        }
        return data;
    }

    var queue = function (status) { return call('/api/moderation/appeals?status=' + status); };

    function setCount(n) { $('rp-count').textContent = n > 0 ? String(n) : ''; $('tab-reports-count').textContent = n > 0 ? String(n) : ''; }

    function person(p) { return p && p.username ? p.username : '?'; }

    function renderList(rows) {
        var list = $('rp-list');
        list.textContent = '';
        if (rows.length === 0) { list.appendChild(el('p', 'rp-empty', state.status === 'open' ? 'No open reports.' : 'Nothing decided yet.')); return; }
        rows.forEach(function (a) {
            var b = el('button', 'rp-row' + (a.id === state.selected ? ' on' : ''));
            b.type = 'button';
            add(b,
                el('strong', null, person(a.record.removed) + ' · ' + a.record.spaceName),
                el('span', null, a.note.length > 110 ? a.note.slice(0, 110) + '…' : a.note),
                el('small', null, a.outcome ? (a.outcome === 'DROPS' ? 'Badge dropped' : 'Badge stayed') + ' · ' + when(a.decidedAt) : 'Sent ' + when(a.createdAt)));
            b.addEventListener('click', function () { state.selected = a.id; renderList(rows); showDetail(a.id); });
            list.appendChild(b);
        });
    }

    async function decide(id, outcome) {
        if (outcome === 'DROPS' && !window.confirm('Drop the badge? The removal itself stays.')) return;
        try {
            await call('/api/moderation/appeals/' + id + '/decide', { method: 'POST', body: JSON.stringify({ outcome: outcome }) });
            say(outcome === 'DROPS' ? 'Badge dropped.' : 'Badge stays.');
            state.selected = null;
            $('rp-detail').textContent = '';
            $('rp-detail').appendChild(el('p', 'rp-empty', 'Pick a report.'));
            load();
        } catch (e) { say(e.message, true); }
    }

    async function showDetail(id) {
        var box = $('rp-detail');
        var d;
        try { d = await call('/api/moderation/appeals/' + id); }
        catch (e) { box.textContent = ''; box.appendChild(el('p', 'rp-empty', e.message)); return; }
        var r = d.record;
        box.textContent = '';
        add(box, add(el('div', 'rp-head'), el('strong', null, r.spaceName), el('span', 'rp-tag', d.outcome ? (d.outcome === 'DROPS' ? 'Badge dropped' : 'Badge stayed') : 'Open'), el('small', null, when(r.createdAt))));
        add(box, el('p', null, person(r.removedBy) + ' removed ' + person(r.removed) + ': ' + r.reason));
        if (r.addresses && r.addresses.length) {
            var said = el('div', 'rp-said');
            r.addresses.forEach(function (a) { add(said, add(el('div'), el('small', null, person(a.by) + ' · ' + when(a.createdAt)), el('p', null, a.body))); });
            add(box, el('h3', null, 'What each side said afterwards'), said);
        }
        add(box, el('h3', null, person(r.removed) + "'s report"), el('p', 'rp-note', d.note));
        add(box, el('h3', null, 'The room, a day before to an hour after'));
        if (d.history.length === 0) add(box, el('p', 'rp-empty', 'Nothing was said in that window.'));
        else {
            var hist = el('div', 'rp-history');
            d.history.forEach(function (l) {
                add(hist, add(el('div', l.system ? 'rp-line sys' : 'rp-line'), el('small', null, when(l.createdAt) + ' · ' + (l.system ? 'system' : l.sender)), el('p', null, l.body)));
            });
            add(box, hist);
        }
        if (d.outcome) { add(box, el('p', 'rp-empty', 'Decided by ' + (d.decidedBy || '?') + ' · ' + when(d.decidedAt))); return; }
        var stays = el('button', null, 'Badge stays'), drops = el('button', 'primary', 'Drop the badge');
        stays.type = drops.type = 'button';
        stays.addEventListener('click', function () { decide(id, 'STICKS'); });
        drops.addEventListener('click', function () { decide(id, 'DROPS'); });
        add(box, el('p', 'rp-hint', 'The removal stands either way. Dropping only takes the badge off the record.'), add(el('div', 'rp-actions'), stays, drops));
    }

    async function load() {
        var want = state.status;
        try {
            var rows = await queue(want);
            var open = want === 'open' ? rows : await queue('open');
            setCount(open.length);
            if (want !== state.status) return;   // the filter changed while this was loading; the newer load draws
            renderList(rows);
        } catch (e) {
            var list = $('rp-list');
            list.textContent = '';
            list.appendChild(el('p', 'rp-empty', e.status === 403 ? 'Reports are for moderators.' : e.message));
        }
    }

    async function count() { try { setCount((await queue('open')).length); } catch (e) { /* the tab is hidden for non-moderators anyway */ } }

    async function signOut() { try { await call('/api/auth/logout', { method: 'POST' }); } catch (e) { /* the page reloads into the gate regardless */ } }

    document.querySelectorAll('#rp-filter button').forEach(function (b) {
        b.addEventListener('click', function () {
            state.status = b.dataset.status;
            state.selected = null;
            document.querySelectorAll('#rp-filter button').forEach(function (o) { o.classList.toggle('on', o === b); });
            $('rp-detail').textContent = '';
            $('rp-detail').appendChild(el('p', 'rp-empty', 'Pick a report.'));
            load();
        });
    });

    window.AdminReports = { load: load, count: count, signOut: signOut };
})();
