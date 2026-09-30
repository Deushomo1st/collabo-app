# switch

An on/off toggle. Underneath it is a real `<input type="checkbox">`, so keyboard, focus and screen readers work.
Made for the Spaces screen (permissions, pleas), reusable anywhere.

| File | Role |
|---|---|
| `switch.js` | `createSwitch()` + `preloadSwitch()` (ES module) |
| `switch.css` | Styles, loaded once as a global `<link>` |

## Quick start

```js
import { createSwitch, preloadSwitch } from '/js/components/switch/switch.js';

await preloadSwitch();   // optional: avoids a flash of unstyled switch
const sw = createSwitch({ checked: true, label: 'Pleas', onChange: (on) => save(on) });
host.append(sw.element);
```

## API

### `createSwitch(options) → { element, input, setChecked }`

| Option | Type | Default | Notes |
|---|---|---|---|
| `checked` | boolean | `false` | Initial state. |
| `disabled` | boolean | `false` | Greyed out, not focusable. |
| `label` | string | — | Accessible name (there is no visible text, so always pass one). |
| `onChange` | `(checked, event) => void` | — | Fires on every user toggle. |

`sw.input.checked` reads the state. `sw.setChecked(bool)` sets it from code without firing `onChange`.

## Theming

Public variables: `--switch-height` (28px, width follows at 1.8×), `--switch-on`, `--switch-off`, `--switch-knob`.
Light values are in `css/global/theme.css`.

## Editing this component

- Every class starts with `switch` (`.switch__track`, …). Component CSS is a global `<link>`, so unprefixed classes can collide.
- Set the public `--switch-*` variables from outside; the component reads private `--_sw-*` copies. Never set those.
- Light values go in `css/global/theme.css` under `:root[data-theme="light"]`.
- Test in the gallery (`/HTML-pages/components.html`, page 12) in both themes, then hard-refresh (Ctrl+Shift+R).

## Recipes

### A bigger switch
Set `--switch-height: 36px` on the element or an ancestor. Width and knob scale with it.

### A different "on" colour
`style="--switch-on: #209EBB"` on `sw.element`.
