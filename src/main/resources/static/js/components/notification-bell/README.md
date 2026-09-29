# notification-bell

A bell hanging on a string from the top edge, with an unread badge. Clicking it swings the bell and
opens a glass panel with search and a date-range filter. New unread items make the icon ring.
Ported from SchoolHub (`style.css` `.notif-*`, `modal.css` `.notif-panel`, `app.js`).

**Depends on:** [`glass-blur-dialog`](../glass-blur-dialog/README.md) (the panel) and
[`calendar`](../calendar/README.md) (the date filter). Both are imported automatically.

| File | Role |
|---|---|
| `notification-bell.js` | Loader + API (ES module) |
| `notification-bell.css` | Bell + panel styles, loaded once |
| `notification-bell.html` | Bell fragment + `<template data-part="panel">` for the panel content |

## Quick start

```js
import { mountNotificationBell } from '/js/components/notification-bell/notification-bell.js';

const bell = await mountNotificationBell('body', {
    loadItems: async () => (await fetch('/api/notifications')).json(),
    onRead: (n) => fetch(`/api/notifications/${n.id}/read`, { method: 'POST' }),
    onSelect: (n) => { /* navigate to what the notification is about */ },
});
```

The component has **no backend of its own**: you supply the data and handle reads and navigation.

## API

### `mountNotificationBell(target, opts) → Promise<handle>`

| Option | Type | Default | Notes |
|---|---|---|---|
| `loadItems` | `async () => Item[]` | returns `[]` | Called on mount, every `pollMs`, and when the panel opens. |
| `onRead` | `(item) => void` | — | Called once when an unread item is clicked (already marked read locally). |
| `onSelect` | `(item) => void` | — | Called after the panel closes, for every clicked item. |
| `isActionable` | `(item) => boolean` | `() => false` | Actionable items get a brand-tinted hover. |
| `pollMs` | number | `60000` | `0` turns polling off. |
| `inline` | boolean | `false` | In normal flow instead of fixed top-right (previews). |

**Item:** `{ id, title, body?, createdAt?, read? }`. `createdAt` can be an ISO string or a `Date`.
The date filter compares ISO strings by their first 10 characters (so `…Z` timestamps filter by UTC day)
and `Date`s by local day.

**Handle:** `refresh()` (re-run `loadItems`; updates an open panel too), `open()`, `destroy()`, `element`.
Call `destroy()` when removing it, to stop polling.

## Theming

The panel is a dialog on `<body>`, so set variables on `:root`. Public variables:
`--notification-bell-` + `bg`, `fg`, `shadow`, `shadow-hover`, `icon-filter`, `string`, `badge-bg`, `brand`,
`muted`, `line`, `item-line`, `item-hover-bg`, `item-hover-accent`, `item-hover-shadow`, `item-unread-bg`, `z`.
Light values live in `css/global/theme.css`. The panel's glass, text and inputs come from glass-blur-dialog.

## Accessibility

The bell's label includes the unread count ("Notifications, 3 unread"). Items are buttons. The badge caps
at `99+`. Swing and ring animations are skipped under `prefers-reduced-motion`.

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.notification-bell__part`, `.notification-bell--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Public vs private variables.** Pages set the public `--notification-bell-*` variables. The component only reads its
  private `--_nb-*` copies. Never set a private variable from outside.
- **Light theme values go in `css/global/theme.css`**, under `:root[data-theme="light"]`. Dusk is the built-in default.
- **Test in the gallery** (`/HTML-pages/components.html`, page 6) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Connect it to a real backend

```js
const bell = await mountNotificationBell('body', {
    loadItems: async () => (await fetch('/api/notifications', { credentials: 'include' })).json(),
    onRead: (n) => fetch(`/api/notifications/${n.id}/read`, { method: 'POST', credentials: 'include' }),
    onSelect: (n) => { if (n.link) location.href = n.link; },
    isActionable: (n) => !!n.link,
});
```
The items must match `{ id, title, body?, createdAt?, read? }`; map your API's fields in `loadItems` if they differ.

### Push new notifications instantly (WebSocket or SSE) instead of polling

1. Mount with `pollMs: 0`.
2. When your socket reports a new notification, call `bell.refresh()`. The badge updates, the bell rings,
   and an open panel re-renders.

### Move the bell, or shorten its string

The bell is fixed at `top: 28px; right: 32px;`, and the string is the `height: 28px` in `.notification-bell::before`.
Change **both** 28px values together, or the bell will float away from its string.
The swing pivots at `transform-origin: 50% -28px` on `.notification-bell__button`, so update that too.

### Add a field to each item (for example a sender)

1. In `render()` inside `notification-bell.js`, add a `<span class="notification-bell__item-sender">` and set its
   `textContent` (never `innerHTML`, since notification text comes from users).
2. Style `.notification-bell__item-sender` in `notification-bell.css`.
3. **Verify:** gallery page 6. Add `sender` to the demo items in `components.html`.
