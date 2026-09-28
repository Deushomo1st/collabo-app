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
