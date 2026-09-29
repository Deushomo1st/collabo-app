# tilt-stack

A side-scrolling row of overlapping, 3D-tilted 4:3 cards, one per person (or item). Hovering or
focusing a card raises it, straightens it, and floats its name above. Drag, wheel or swipe scrolls the row.
Ported from SchoolHub `app/tiltstack`, where it heads every people list (staff, students, guardians…).

| File | Role |
|---|---|
| `tilt-stack.js` | API (ES module) |
| `tilt-stack.css` | Styles, loaded once as a global `<link>` |

There's no `.html` fragment: every card is built from data, so the markup lives in the JS.

## Quick start

```js
import { renderTiltStack, preloadTiltStack } from '/js/components/tilt-stack/tilt-stack.js';

await preloadTiltStack();   // optional, avoids a flash of unstyled cards on first render

const stack = renderTiltStack(document.querySelector('#people'), [
    { id: 1, name: 'Ada Obi', subtitle: 'Teacher', avatar: '/img/ada.jpg', status: 'active' },
    { id: 2, name: 'Tunde Bello', subtitle: 'Bursar' },             // no avatar -> initials "TB"
], {
    showStatus: true,
    statusTone: (s) => ({ active: 'ok', suspended: 'danger' }[s] || ''),
    onClick: (person) => { /* open their profile */ },
});

stack.highlight(2);   // scroll to Tunde's card and keep it raised
```

## API

### `renderTiltStack(container, items, opts) → { element, highlight(id), destroy() }`

Synchronous. Replaces whatever is in `container`. To filter or refresh, call it again on the same container.

**Item:** `{ id, name, subtitle?, avatar?, status? }`. `avatar` is an image URL (a data URL works too).
Missing or broken images fall back to the name's initials.

| Option | Type | Notes |
|---|---|---|
| `onClick` | `(item) => void` | Makes cards clickable (Enter/Space too). The click that ends a drag is ignored. |
| `showStatus` | boolean | Show `status` as a pill on the card. |
| `statusTone` | `(status) => 'ok' \| 'warn' \| 'danger' \| ''` | Colours the pill. |
| `emptyText` | string | Shown when `items` is empty. Default `'Nothing to show yet.'` |
| `highlightId` | id | Raise this card on render, without scrolling. |
| `label` | string | Accessible name for the row. |

### `highlightTiltCard(container, id) → boolean`

The same as `handle.highlight(id)`, for when you only have the container. SchoolHub uses it so that
clicking a table row spotlights the matching card:

```js
row.onclick = () => highlightTiltCard(stackHost, row.dataset.id);
```

## Behaviour

- **Mouse:** drag to scroll. **Touch:** native swipe. **Wheel:** vertical wheel scrolls sideways,
  but once the row reaches either end the page scrolls normally again.
- Cards are focusable; focus raises them the same way hover does.

**Changed from SchoolHub:** the wheel no longer traps page scrolling at the ends of the row; drag is
mouse-only (touch drags scrolled twice); images can't be ghost-dragged; status pills no longer need
SchoolHub's global `.badge` class.

## Theming

Shared with [`avatar`](../avatar/README.md), so both sit at the same angle (unprefixed on purpose):

```css
:root { --card-tilt: rotateY(34deg) rotateX(8deg); --card-tilt-hover: rotateY(14deg) rotateX(3deg); }
```

Public variables: `--tilt-stack-` + `brand`, `card`, `ink`, `muted`, `shadow`, `shadow-raised`,
`name-shadow`, `card-width` (190px), `overlap` (-74px). Light values live in `css/global/theme.css`.

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.tilt-stack__part`, `.tilt-stack--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Public vs private variables.** Pages set the public `--tilt-stack-*` variables. The component only reads its
  private `--_ts-*` copies. Never set a private variable from outside.
- **Light theme values go in `css/global/theme.css`**, under `:root[data-theme="light"]`. Dusk is the built-in default.
- **Test in the gallery** (`/HTML-pages/components.html`, page 9) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Link a table (or list) to the stack, as SchoolHub does

```js
const stack = renderTiltStack(stackHost, people, { onClick: (p) => highlightRow(p.id) });
tableBody.querySelectorAll('tr[data-id]').forEach((tr) => {
    tr.onclick = () => stack.highlight(tr.dataset.id);
});
```
Card click → highlight the table row; row click → spotlight the card.

### Filter or search

Call `renderTiltStack` again on the same container with the filtered array. It replaces the old row cleanly,
and the drag and wheel listeners go with it.

### Bigger cards, or less overlap

```css
#people { --tilt-stack-card-width: 240px; --tilt-stack-overlap: -40px; }
```
Set them on the container (or any ancestor). A less negative overlap spreads the cards out.

### Add a status colour (for example `info`)

1. In `tilt-stack.css`: `.tilt-stack__status--info { color: #8ECAE6; }`
2. Return `'info'` from your `statusTone` for the statuses that should use it.

### Change the tilt of every card (and avatars too)

```css
:root { --card-tilt: rotateY(24deg) rotateX(6deg); --card-tilt-hover: rotateY(8deg) rotateX(2deg); }
```
These two variables are shared with `avatar` on purpose. **Verify:** gallery pages 9 **and** 10.
