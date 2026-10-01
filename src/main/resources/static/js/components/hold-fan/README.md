# hold-fan

Press and hold a button and up to five round buttons fan out from behind it, all at once. Keep the finger down and slide onto one,
then let go: that is the pick (the one under the finger grows). Let go anywhere else and the fan stays open to tap. A plain tap is
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
| `actions` | `[]` | 1 to 3. Left, top, right (with 3). `icon` is static SVG markup, never user text. |
| `holdMs` | `350` | How long to hold before the fan opens. Moving more than 10px cancels it (that is a drag). |
| `stagger` | `0` | ms between each button leaving (and returning); 0 = all at once. |
| `idleMs` | `4000` | The fan tucks away by itself after this long untouched. |

Handle: `open()`, `close()`, `destroy()`.

## Behaviour

- The release that ends a hold is swallowed, so it does not also count as a tap.
- Closes on: choosing a button, pressing anywhere else, Escape, or the idle timeout.
- Keyboard: Enter/Space is a normal click; **ArrowUp** on the trigger opens the fan and focuses the first button.
- Phones: the long-press context menu is suppressed on the trigger.
- Buttons are kept on screen when the trigger sits near an edge. `prefers-reduced-motion` drops the movement.
- Public variables: `--hold-fan-{bg,ink,border,shadow,z}`. Light-theme values are in `css/global/theme.css`.
