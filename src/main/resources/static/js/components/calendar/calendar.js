// calendar: month grid + From/To range picker.
// Ported from SchoolHub app/calendar (renderMonthGrid / openDualCalendar).
// Depends on glass-blur-dialog (the range picker opens inside one).
//
// Usage:
//   import { renderCalendarMonth, openCalendarRange, preloadCalendar }
//       from '/js/components/calendar/calendar.js';
//
//   // Inline single month (e.g. an agenda card). Add class "calendar--agenda" to the host for the card width.
//   renderCalendarMonth(document.querySelector('#agenda'), {
//       view: { y: 2026, m: 8 },                          // m is 0-based (8 = September)
//       marks: { '2026-09-28': ['event', 'exam'] },      // up to 3 dots: event | announcement | holiday | exam
//       onPick: (date) => {},
//   });
//
//   // From/To range in a dialog. Dates are 'YYYY-MM-DD' strings, read as LOCAL dates.
//   openCalendarRange({ from: '2026-09-01', to: '2026-09-30', onApply: (fromIso, toIso) => {} });

import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';

const BASE = '/js/components/calendar/calendar';
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEKDAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

let fragmentPromise = null;

export function preloadCalendar() {
    if (!fragmentPromise) {
        fragmentPromise = Promise.all([
            loadStylesOnce(BASE + '.css', 'calendar'),
            fetch(BASE + '.html').then((res) => {
                if (!res.ok) throw new Error(`calendar: fragment ${res.status}`);
                return res.text();
            }),
        ]).then(([, html]) => html).catch((err) => {
            fragmentPromise = null;
            throw err;
        });
    }
    return fragmentPromise;
}

// Render one month into `host`.
// cfg: { view:{y,m}, selected, min, max, marks, onPick(date), onNav() }
// onNav defaults to re-rendering the same host, so a standalone grid navigates on its own.
export function renderCalendarMonth(host, cfg = {}) {
    loadStylesOnce(BASE + '.css', 'calendar');
    host.classList.add('calendar');

    const now = new Date();
    cfg.view = cfg.view || { y: now.getFullYear(), m: now.getMonth() };
    const onNav = cfg.onNav || (() => renderCalendarMonth(host, cfg));
    const onPick = cfg.onPick || (() => {});

    const { y, m } = cfg.view;
    const startWd = (new Date(y, m, 1).getDay() + 6) % 7;   // week starts Monday
    const days = new Date(y, m + 1, 0).getDate();
    const prevDays = new Date(y, m, 0).getDate();
    const todayIso = iso(now);

    let cells = '';
    for (let i = 0; i < startWd; i++) {
        cells += `<span class="calendar__day calendar__day--outside">${prevDays - startWd + 1 + i}</span>`;
    }
    for (let d = 1; d <= days; d++) {
        const date = new Date(y, m, d);
        const off = (cfg.min && date < dayStart(cfg.min)) || (cfg.max && date > dayStart(cfg.max));
        const dIso = iso(date);
        const marks = cfg.marks && cfg.marks[dIso];
        const dots = marks
            ? '<span class="calendar__dots">'
                + marks.slice(0, 3).map((t) => `<span class="calendar__dot calendar__dot--${t}"></span>`).join('')
                + '</span>'
            : '';
        const classes = ['calendar__day'];
        if (sameDay(date, cfg.selected)) classes.push('calendar__day--selected');
        if (off) classes.push('calendar__day--disabled');
        if (dIso === todayIso) classes.push('calendar__day--today');
        if (marks) classes.push('calendar__day--marked');
        cells += `<button type="button" class="${classes.join(' ')}" data-d="${d}"${off ? ' disabled' : ''}`
            + ` aria-label="${date.toLocaleDateString(undefined, { dateStyle: 'full' })}">${d}${dots}</button>`;
    }
    const trail = (7 - (startWd + days) % 7) % 7;
    for (let t = 1; t <= trail; t++) cells += `<span class="calendar__day calendar__day--outside">${t}</span>`;

    host.innerHTML =
        '<div class="calendar__head">'
        + '<button type="button" class="calendar__nav" data-nav="pm" aria-label="Previous month">&lsaquo;</button>'
        + '<div class="calendar__title-wrap" title="Scroll to change month">'
        +   '<button type="button" class="calendar__chev" data-nav="py" aria-label="Previous year">&#9650;</button>'
        +   `<span class="calendar__title">${MONTHS[m]} ${y}</span>`
        +   '<button type="button" class="calendar__chev" data-nav="ny" aria-label="Next year">&#9660;</button>'
        + '</div>'
        + '<button type="button" class="calendar__nav" data-nav="nm" aria-label="Next month">&rsaquo;</button>'
        + '</div>'
        + '<div class="calendar__grid" aria-hidden="true">'
        + WEEKDAYS.map((w) => `<span class="calendar__weekday">${w}</span>`).join('')
        + '</div>'
        + `<div class="calendar__grid">${cells}</div>`;

    function stepMonth(n) {
        const next = new Date(cfg.view.y, cfg.view.m + n, 1);
        cfg.view.y = next.getFullYear();
        cfg.view.m = next.getMonth();
        onNav();
    }
    host.querySelector('[data-nav="pm"]').onclick = () => stepMonth(-1);
    host.querySelector('[data-nav="nm"]').onclick = () => stepMonth(1);
    host.querySelector('[data-nav="py"]').onclick = () => stepMonth(-12);
    host.querySelector('[data-nav="ny"]').onclick = () => stepMonth(12);
    host.querySelector('.calendar__title-wrap').addEventListener('wheel', (e) => {
        e.preventDefault();
        stepMonth(e.deltaY < 0 ? -1 : 1);
    }, { passive: false });
    host.querySelectorAll('.calendar__day[data-d]:not(.calendar__day--disabled)').forEach((b) => {
        b.onclick = () => onPick(new Date(y, m, parseInt(b.dataset.d, 10)));
    });
}

// From/To range picker in a glass-blur-dialog. Resolves to the dialog handle ({ close, panel }).
// "To" can't be earlier than "From" and vice versa (out-of-range days are disabled).
export async function openCalendarRange(opts = {}) {
    const html = await preloadCalendar();

    let fromDate = parseDate(opts.from);
    let toDate = parseDate(opts.to);
    const base = fromDate || new Date();
    const fromView = { y: base.getFullYear(), m: base.getMonth() };
    const toBase = toDate || fromDate || new Date();
    const toView = { y: toBase.getFullYear(), m: toBase.getMonth() };

    const dlg = await openGlassBlurDialog({ size: 'lg', label: opts.label || 'Choose a date range', html });
    const panel = dlg.panel;
    const fromHost = panel.querySelector('.calendar__pane--from');
    const toHost = panel.querySelector('.calendar__pane--to');
    const rangeText = panel.querySelector('.calendar__range-text');

    function draw() {
        renderCalendarMonth(fromHost, {
            view: fromView, selected: fromDate, max: toDate,        // From can't be after To
            onPick: (d) => { fromDate = d; if (toDate && toDate < fromDate) toDate = null; draw(); },
            onNav: draw,
        });
        renderCalendarMonth(toHost, {
            view: toView, selected: toDate, min: fromDate,          // To can't be before From
            onPick: (d) => { toDate = d; if (fromDate && fromDate > toDate) fromDate = null; draw(); },
            onNav: draw,
        });
        rangeText.textContent = `From ${nice(fromDate)} to ${nice(toDate)}`;
    }

    panel.querySelector('[data-action="cancel"]').onclick = dlg.close;
    panel.querySelector('[data-action="clear"]').onclick = () => { fromDate = null; toDate = null; draw(); };
    panel.querySelector('[data-action="apply"]').onclick = () => {
        if (typeof opts.onApply === 'function') opts.onApply(iso(fromDate), iso(toDate));
        dlg.close();
    };
    draw();
    return dlg;
}

// ---- helpers ----

function iso(d) {
    return d ? `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` : null;
}
function dayStart(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
function sameDay(a, b) {
    return !!(a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate());
}
function nice(d) { return d ? d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '…'; }

// 'YYYY-MM-DD' is read as a LOCAL date. new Date('2026-09-01') would be UTC midnight,
// which shows as Aug 31 anywhere west of UTC.
function parseDate(value) {
    if (!value) return null;
    if (value instanceof Date) return dayStart(value);
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    return m ? new Date(+m[1], +m[2] - 1, +m[3]) : new Date(value);
}

function loadStylesOnce(href, componentName) {
    const existing = document.querySelector(`link[data-component="${componentName}"]`);
    if (existing) return existing.sheet ? Promise.resolve() : new Promise((r) => existing.addEventListener('load', r, { once: true }));
    return new Promise((resolve, reject) => {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = href;
        link.dataset.component = componentName;
        link.onload = resolve;
        link.onerror = () => reject(new Error(`${componentName}: stylesheet failed to load`));
        document.head.appendChild(link);
    });
}
