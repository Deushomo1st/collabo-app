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

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.glass-module__part`, `.glass-module--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Public vs private variables.** Pages set the public `--glass-module-*` variables. The component only reads its
  private `--_gm-*` copies. Never set a private variable from outside.
- **Light theme values go in `css/global/theme.css`**, under `:root[data-theme="light"]`. Dusk is the built-in default.
- **Test in the gallery** (`/HTML-pages/components.html`, page 7) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Make one module stronger or softer than the rest

Set the public variables on that element only:
```html
<section class="glass-module" style="--glass-module-blur: blur(30px) saturate(170%); --glass-module-radius: 12px;">
```

### Give modules a tint

The module is deliberately transparent. To tint it, add a background yourself:
```css
.my-panel.glass-module { background: color-mix(in srgb, #209EBB 8%, transparent); }
```
Keep the tint below about 15%, or it stops reading as glass.

### Nothing seems to happen

The module has no background of its own, so over a flat colour it's nearly invisible. Test it over an image,
a gradient, or busy content, as gallery page 7 does. Also check that the browser supports `backdrop-filter`.
