# nav-selector-fluid-hold

A pill-shaped nav with a sliding "active" bubble. It collapses to a single icon (the active
section's) when you scroll down, and expands again when you scroll up, click the icon, or hover it.
Long link lists scroll sideways with snap, edge fades, and arrow buttons.

| File | Role |
|---|---|
| `nav-selector-fluid-hold.js` | Loader + API (ES module) |
| `nav-selector-fluid-hold.css` | Styles, loaded once as a global `<link>` |
| `nav-selector-fluid-hold.html` | Nav fragment with 6 default links |

**Browser support:** Chromium 125+ (CSS anchor positioning and `:has()`).
**One per page:** anchor names (`--ns-active`, `--ns-hover`, `--ns-nav`) are document-wide.

## Quick start

```js
import { mountNavSelector } from '/js/components/nav-selector-fluid-hold/nav-selector-fluid-hold.js';

const nav = await mountNavSelector('#nav', {
    links: ['Dashboard', 'Projects', 'Team'],
    hrefs: ['#dashboard', '#projects', '#team'],
    activeIndex: 0,
    onChange: (label, href) => { /* show that section */ },
});
```

## API

### `mountNavSelector(target, options) → Promise<{ element, destroy } | undefined>`

`target` is a selector or an element. Resolves to `undefined` if the target doesn't exist.

| Option | Type | Default | Notes |
|---|---|---|---|
| `links` | string[] | 6 demo links | Labels. Icons cycle through 6 built-in SVGs. |
| `hrefs` | string[] | `'#'` | One per link. Clicks call `preventDefault()`; navigate in `onChange`. |
| `activeIndex` | number | `0` | Initially active link. |
| `onChange` | `(label, href) => void` | — | Called when a link is clicked. |
| `idleMs` | number | `1500` | After expanding mid-page, re-collapse after this much idle time. |
| `scrollRoot` | Element \| selector | `window` | **What scrolls.** Set this when your content scrolls inside a container rather than the page. |
| `threshold` | number | `40` | Within this many px of the top, the nav always stays expanded. |

**Handle:** `destroy()` removes the nav and its scroll/resize listeners. Call it when removing the nav,
or listeners pile up each time you remount it.

## When does it collapse?

Only when **`scrollRoot` scrolls down past `threshold`**. If nothing scrolls (a short page, or content that
scrolls inside a container you didn't pass as `scrollRoot`), it stays expanded. That's by design, not a bug.
To check whether the page can scroll, run in the Console:

```js
document.documentElement.scrollHeight - innerHeight   // must be > 40 for window scrolling to collapse it
```

## Sizing

Set these on the mount target or any ancestor:

| Variable | Default | Controls |
|---|---|---|
| `--nav-width` | `min(92vw, 560px)` | Max width when expanded |
| `--nav-padding` | `6px` | Shell padding |
| `--nav-link-padding` | `10px 20px` | Per-link padding |
| `--nav-font-size` | `15px` | Link font size |

The nav mounts inside a sticky wrapper (`.nav-selector-fluid-hold-wrap`, `top: 16px`) so it stays
reachable on long pages. It is dark in both themes; it doesn't read the theme variables.

## Known quirk

The HTML is injected with `fetch()`, and Chromium sometimes measures anchor positions before layout.
The loader forces a reflow (`void target.offsetHeight`) right after inserting it; keep that line,
or the active and hover bubbles can render with zero size.

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.nav-selector-fluid-hold__part`, `.nav-selector-fluid-hold--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Variables:** only the four sizing variables (`--nav-width`, `--nav-padding`, `--nav-link-padding`,
  `--nav-font-size`) are public. Its colours are hard-coded, so it doesn't respond to the theme yet
  (see the recolour recipe below).
- **One per page:** the anchor names (`--ns-active`, `--ns-hover`, `--ns-nav`) are document-wide. Don't reuse them
  in another component.
- **Test in the gallery** (`/HTML-pages/components.html`, page 1) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Your page scrolls inside a container, and the nav never collapses

Pass that container as `scrollRoot`:
```js
await mountNavSelector('#nav', { scrollRoot: document.querySelector('main') });
```
Run `el.scrollHeight - el.clientHeight` on the container in the Console: it must be more than `threshold` (40).

### Use your own icons

Icons come from the `ICONS` array in `nav-selector-fluid-hold.js`, cycling by position. To choose them per link:

1. Accept `options.icons` (an array of SVG strings) and use `options.icons?.[i] ?? ICONS[i % ICONS.length]`
   in the `links` loop.
2. Document `icons` in the API table above.

### Collapse sooner or later, or never re-collapse on idle

`threshold` sets how far from the top it stays expanded; `idleMs` sets the re-collapse delay after expanding
mid-page. For "never on idle", pass a very large `idleMs` (for example `1e9`).

### Recolour it

The colours are hard-coded (dark greys, white bubble), not variables. To theme it, turn each colour into a
public variable the way the other components do, for example
`background: linear-gradient(145deg, var(--nav-selector-bg-from, #2a2a2a), var(--nav-selector-bg-to, #111));`,
then add light values to `css/global/theme.css`.
