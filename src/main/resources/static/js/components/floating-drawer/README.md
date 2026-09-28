# floating-drawer

A macOS-style drawer hanging from the top-left corner. Hovering peeks the icons; clicking the tab
expands the labels. The tab mirrors the active section's icon so you always know where you are.
Ported from SchoolHub `app/drawer` plus the nav wiring from `dashboards.js`.

| File | Role |
|---|---|
| `floating-drawer.js` | Loader + API (ES module) |
| `floating-drawer.css` | Styles, loaded once as a global `<link>` |
| `floating-drawer.html` | Drawer fragment, fetched once and cached |

## Quick start

```js
import { mountFloatingDrawer } from '/js/components/floating-drawer/floating-drawer.js';

const drawer = await mountFloatingDrawer('body', {
    activeId: 'home',
    items: [
        { id: 'home', label: 'Home', icon: '<svg …>…</svg>' },
        { id: 'people', label: 'People', icon: '<svg …>…</svg>' },
    ],
    footer: [{ id: 'logout', label: 'Sign out', icon: '<svg …>…</svg>' }],
    onSelect: (item) => { /* show that section */ },
});
```

## API

### `mountFloatingDrawer(target, opts) → Promise<handle>`

`target` is a selector or element. The drawer is `position: fixed` to the viewport unless `inline` is set.

| Option | Type | Default | Notes |
|---|---|---|---|
| `items` | `{ id, label, icon }[]` | `[]` | Main sections. `icon` is SVG markup. |
| `footer` | same | `[]` | Pinned at the bottom, styled as destructive (red). Selecting one does **not** make it active. Hidden when empty. |
| `activeId` | string | — | Initially active item. |
| `onSelect` | `(item) => void` | — | Called after the drawer closes. |
| `triggerIcon` | SVG string | layers icon | Tab icon when nothing is active (or mirroring is off). |
| `mirrorActiveIcon` | boolean | `true` | Tab shows the active item's icon. |
| `inline` | boolean | `false` | `position: absolute` inside a positioned parent (previews). |

**Handle:** `setItems(items)`, `setFooter(items)`, `setActive(id)`, `dismiss()`, `destroy()`, `element`.
Call `destroy()` when removing it: the drawer listens on `document` for outside clicks and Escape.

## Behaviour

| Stage | How | Shows |
|---|---|---|
| 0 closed | default | Tab only |
| 1 open | mouse hover | Icons |
| 2 expanded | click the tab | Icons + labels (width fits the longest label) |

Mouse leave closes a hover-opened drawer (after 2s from expanded, 150ms from open). A click-opened drawer
stays until you click the tab again, click outside, press Escape, or pick an item.
Closed items are `inert`, so keyboard users can't tab into the hidden list.

**Changed from SchoolHub:** clicking the tab while hover-peeking now expands it (the original closed it,
so a mouse user could never reach the labels); trigger and items are real `<button>`s; Escape closes it.

## Theming

The drawer is dark metal in both themes; only accents change. Public variables:
`--floating-drawer-height` (440px), `-line`, `-accent` (item hover), `-danger`, `-z` (50).
Light values live in `css/global/theme.css`.
