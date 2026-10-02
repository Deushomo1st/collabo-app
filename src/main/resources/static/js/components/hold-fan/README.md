# hold-fan

Press and hold a button and up to five round buttons fan out from behind it, all at once. Keep the finger down and push toward one
(the pointer hides and snaps to the buttons' angles, and the lit one grows; a mouse is pointer-locked, so it can't leave the window and
comes back on the icon when you let go; losing the window or pressing Escape cancels with no pick), then let go: that is the pick, and the fan closes on
release, picked or not. Opened from the keyboard (ArrowUp) it stays until you pick one, press Escape or tap elsewhere. A plain tap is
untouched: the trigger's own click still fires. With five, one sits dead centre above the trigger.
Made for the collapsed nav icon (`nav-selector-fluid-hold`'s `holdActions` option mounts it), but it works on any button.

| File | Role |
|---|---|
| `hold-fan.js` | `mountHoldFan(trigger, opts)` (ES module) |
| `hold-fan.css` | Styles, loaded once |

```js
import { mountHoldFan } from '/js/components/hold-fan/hold-fan.js';

const fan = mountHoldFan(button, {
    actions: [
        { label: 'Home',   icon: '<svg>…</svg>', onSelect: () => (location.href = '/') },
        { label: 'Report', icon: '<svg>…</svg>', onSelect: report },
        { label: 'Back',   icon: '<svg>…</svg>', onSelect: () => history.back() },
    ],
});
fan.destroy();   // when the trigger goes away
```

| Option | Default | Notes |
|---|---|---|
| `actions` | `[]` | 1 to 5, left to right (with 5 one sits dead centre). `icon` is static SVG markup, never user text. |
| `holdMs` | `350` | How long to hold before the fan opens. Moving more than 10px cancels it (that is a drag). |
| `stagger` | `0` | ms between each button leaving (and returning); 0 = all at once. |

Handle: `open()`, `close()`, `destroy()`.

## Behaviour

- The release that ends a hold is swallowed, so it does not also count as a tap.
- Closes on: choosing a button, pressing anywhere else, or Escape. There is no idle timer.
- Keyboard: Enter/Space is a normal click; **ArrowUp** on the trigger opens the fan and focuses the first button.
- Phones: the long-press context menu is suppressed on the trigger.
- Buttons are kept on screen when the trigger sits near an edge. `prefers-reduced-motion` drops the movement.
- Public variables: `--hold-fan-{bg,ink,border,shadow,z}`. Light-theme values are in `css/global/theme.css`.
