# action-banner

A glowing red "Action required" strip: tag, title, a line of text and buttons. For anything that blocks
something until the user answers (a payment claim holding a room, a response clock running down).
Made for the Spaces screen.

| File | Role |
|---|---|
| `action-banner.js` | `createActionBanner()` + `preloadActionBanner()` (ES module) |
| `action-banner.css` | Styles, loaded once as a global `<link>` |

## Quick start

```js
import { createActionBanner, preloadActionBanner } from '/js/components/action-banner/action-banner.js';

await preloadActionBanner();   // optional
const banner = createActionBanner({
    title: 'Amaka O. claims ₦30,000 paid to Tolu A.',
    text: 'Until confirmed this is only a claim, and the room is on hold.',
    actions: [
        { label: 'Confirm receipt', variant: 'ok', onClick: confirmReceipt },
        { label: 'Cancel claim', variant: 'danger', onClick: cancelClaim },
    ],
});
room.prepend(banner.element);
```

## API

### `createActionBanner(options) → { element }`

| Option | Type | Default | Notes |
|---|---|---|---|
| `tag` | string | `'Action required'` | The small pill above the title. |
| `title` | string | — | Bold headline. |
| `text` | string | — | Muted line under it. |
| `actions` | `{ label, variant?, onClick }[]` | `[]` | `variant`: `'ok'` (green fill), `'danger'` (red outline), none (neutral). |
| `note` | string | — | Plain pill shown with the buttons, e.g. "Waiting on the recipient". |

All text is set with `textContent`, so user text is safe. `element` has `role="alert"`.
To remove it, call `element.remove()`.

## Theming

Public variables: `--action-banner-glow` (red), `-ink`, `-muted`, `-bg`, `-ok`, `-ok-ink`, `-line`.
Light values are in `css/global/theme.css`. The glow pulses; `prefers-reduced-motion` turns that off.

## Editing this component

- Every class and the keyframes start with `action-banner`. Component CSS is a global `<link>`, so unprefixed names can collide.
- Set the public `--action-banner-*` variables from outside; the component reads private `--_ab-*` copies.
- Light values go in `css/global/theme.css` under `:root[data-theme="light"]`.
- Test in the gallery (`/HTML-pages/components.html`, page 11) in both themes, then hard-refresh (Ctrl+Shift+R).

## Recipes

### A non-red banner (a warning rather than an alarm)
`style="--action-banner-glow: #fbbf24"` on `banner.element`.

### Update the text when state changes
Build a new banner and `oldBanner.element.replaceWith(newBanner.element)`. It has no internal state to keep in sync.
