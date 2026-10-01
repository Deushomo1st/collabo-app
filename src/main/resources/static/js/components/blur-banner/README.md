# blur-banner

The [action-banner](../action-banner/README.md) with everything removed except the backdrop blur: no glow, no pulsing
animation, no coloured tint or border, no `saturate`. Nothing repaints on its own, so it is the light version for
screens that scroll (the pulse is what makes the action-banner costly).

| File | Role |
|---|---|
| `blur-banner.js` | `createBlurBanner()` + `preloadBlurBanner()` (ES module) |
| `blur-banner.css` | Styles, loaded once as a global `<link>` |

## Quick start

```js
import { createBlurBanner, preloadBlurBanner } from '/js/components/blur-banner/blur-banner.js';

await preloadBlurBanner();   // optional
const banner = createBlurBanner({
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

Same options as the action-banner: `tag` (`'Action required'`), `title`, `text`, `actions` (`{ label, variant: 'ok' | 'danger', onClick }`),
`note` (plain text shown instead of buttons). Returns `{ element }`. All text goes in through `textContent`.

## Variables

`--blur-banner-ink`, `-muted`, `-bg`, `-ok`, `-ok-ink`, `-line`, `-blur` (the blur radius, default `18px`).
`-bg` is a faint neutral so the text stays readable; set it to `transparent` for blur and nothing else.

## Not kept from the action-banner

The red glow and its animation, the tinted background and border, the coloured tag, `saturate(150%)`, `overflow: hidden`.
