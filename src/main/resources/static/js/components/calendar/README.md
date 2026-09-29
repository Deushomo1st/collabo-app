# calendar

A month grid (inline) and a From/To date-range picker (in a dialog).
Ported from SchoolHub `app/calendar` (`renderMonthGrid` / `openDualCalendar`).

**Depends on:** [`glass-blur-dialog`](../glass-blur-dialog/README.md) (the range picker opens inside one; it's imported automatically).

| File | Role |
|---|---|
| `calendar.js` | Loader + API (ES module) |
| `calendar.css` | Styles, loaded once as a global `<link>` |
| `calendar.html` | Range-picker layout fragment, fetched once and cached |

## Quick start

```html
<div id="agenda" class="calendar--agenda"></div>

<script type="module">
    import { renderCalendarMonth, openCalendarRange }
        from '/js/components/calendar/calendar.js';

    // Inline month grid
    renderCalendarMonth(document.querySelector('#agenda'), {
        marks: { '2026-09-28': ['event', 'exam'] },
        onPick: (date) => console.log(date),
    });

    // Range picker in a dialog
    openCalendarRange({
        from: '2026-09-01',
        to: '2026-09-30',
        onApply: (fromIso, toIso) => console.log(fromIso, toIso),   // 'YYYY-MM-DD' or null
    });
</script>
```

## API

### `renderCalendarMonth(host, cfg)`

Renders one month into `host` and wires its navigation. Synchronous.

| Option | Type | Default | Notes |
|---|---|---|---|
| `view` | `{ y, m }` | current month | **`m` is 0-based** (8 = September). Mutated as the user navigates. |
| `selected` | Date | — | Highlighted day. |
| `min`, `max` | Date | — | Days outside the range are disabled. |
| `marks` | `{ 'YYYY-MM-DD': string[] }` | — | Up to 3 dots per day: `event`, `announcement`, `holiday`, `exam`. |
| `onPick` | `(date: Date) => void` | no-op | Called when an enabled day is clicked. |
| `onNav` | `() => void` | re-render `host` | Override only if you re-render yourself (the range picker does). |

Navigation: ‹ › change month, ▲ ▼ change year, and the mouse wheel over the month title scrolls months.
Add `calendar--agenda` to the host for the card width (max 380px, centred).

### `openCalendarRange(opts) → Promise<{ close, panel }>`

| Option | Type | Notes |
|---|---|---|
| `from`, `to` | `'YYYY-MM-DD'` or Date | Initial selection. Strings are read as **local** dates. |
| `onApply` | `(fromIso, toIso) => void` | Called on Apply. Either value can be `null` if not chosen. |
| `label` | string | Accessible name. Default `'Choose a date range'`. |

"To" can't be before "From" and vice versa; out-of-range days are disabled.

### `preloadCalendar() → Promise`

Fetches the CSS and fragment once. Optional.

## Theming

Defaults are SchoolHub's dark "dusk" values. Set on any ancestor of an inline grid,
or on `:root` for the range picker (it lives in a dialog on `<body>`):

```css
:root { --calendar-brand: #209EBB; }
```

Public variables: `--calendar-` + `ink`, `muted`, `line`, `brand`, `brand-ink`,
`event`, `announcement`, `holiday`, `exam`. Variables starting `--_cal-` are internal.
The range picker's buttons belong to glass-blur-dialog, so theme those with `--glass-blur-dialog-*`.

## Gotchas

- `new Date('2026-09-01')` is UTC midnight, which is the previous day west of UTC. This component parses `'YYYY-MM-DD'` as local, so pass strings rather than pre-parsed Dates when in doubt.
- Picking a day re-renders the month, so keyboard focus returns to the dialog; Tab resumes from the top.

## Test page

`/HTML-pages/glass-blur-dialog-test.html` includes an inline grid with marks and the range picker.

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.calendar__part`, `.calendar--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Public vs private variables.** Pages set the public `--calendar-*` variables. The component only reads its
  private `--_cal-*` copies. Never set a private variable from outside.
- **Light theme values go in `css/global/theme.css`**, under `:root[data-theme="light"]`. Dusk is the built-in default.
- **Test in the gallery** (`/HTML-pages/components.html`, page 4) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Add a new dot type (for example `meeting`)

1. In `calendar.css`, add the private variable to the `.calendar` block:
   `--_cal-meeting: var(--calendar-meeting, #b28dff);`
2. Add the dot style next to the others:
   `.calendar__dot--meeting { background: var(--_cal-meeting); }`
3. If the light theme needs a different colour, add `--calendar-meeting: …;` to `css/global/theme.css`.
4. Use it: `marks: { '2026-10-02': ['meeting'] }`. The JS needs no change: the mark name becomes the class.
5. **Verify:** gallery page 4, with `meeting` added to one day's marks in the calendar entry of `components.html`.

### Start the week on Sunday

1. In `calendar.js`, change `WEEKDAYS` to start with `'SUN'`.
2. Change the offset line `const startWd = (new Date(y, m, 1).getDay() + 6) % 7;` to
   `const startWd = new Date(y, m, 1).getDay();`.
3. **Verify:** gallery page 4. The 1st of the month must fall under the right weekday (check against your OS calendar).

### Limit the range picker to future dates

`openCalendarRange` doesn't take `min`/`max` yet. To add it:

1. In `openCalendarRange`, read a floor date: `const floor = parseDate(opts.min);`
2. In `draw()`, give the **From** grid `min: floor` (it already has `max: toDate`), and give the **To** grid
   `min: fromDate || floor` instead of `min: fromDate`.
3. Callers then pass `min: 'YYYY-MM-DD'`, for example today's date. Document `min` in the API table above.
4. **Verify:** days before `min` are dimmed and can't be clicked in either pane.
