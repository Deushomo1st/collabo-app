# glass-module

A transparent frosted surface for grouping page content: cards, tiles, dashboard panels.
Ported from SchoolHub `style.css` `.glass-module`.

**CSS-only.** There's no markup fragment and no mount function: add the class to any element.
The `.js` file only exists so pages that load components through JS can inject the stylesheet.

| File | Role |
|---|---|
| `glass-module.css` | The `.glass-module` class |
| `glass-module.js` | `loadGlassModule()`: injects the stylesheet once |

## Usage

```html
<link rel="stylesheet" href="/js/components/glass-module/glass-module.css">

<section class="glass-module" style="padding: 1rem 1.2rem">
    <h3>Attendance</h3>
    <p>96% this week.</p>
</section>
```

or from JS:

```js
import { loadGlassModule } from '/js/components/glass-module/glass-module.js';
await loadGlassModule();
```

It has **no background colour**, only blur, a hairline border and a soft top highlight. Over a flat
background it's nearly invisible; it needs something colourful or busy behind it. Padding is up to you.

## Theming

Public variables: `--glass-module-blur`, `-border`, `-shadow`, `-highlight`, `-radius` (20px).
Set them on the element, any ancestor, or `:root`. Light values live in `css/global/theme.css`.
