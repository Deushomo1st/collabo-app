# nav-selector-fluid-hold

A pill-shaped nav with a sliding "active" bubble. It collapses to a single icon (the active
section's) when you scroll down, and expands only when you tap the icon (or hover it). Scrolling never opens it.
Long link lists scroll sideways with snap, edge fades, and arrow buttons.

| File | Role |
|---|---|
| `nav-selector-fluid-hold.js` | Loader + API (ES module) |
| `nav-selector-fluid-hold.css` | Styles, loaded once as a global `<link>` |
| `nav-selector-fluid-hold.html` | Nav fragment with 6 default links |

**Browser support:** Chromium 125+ (CSS anchor positioning and `:has()`).
**Several per page:** needs Chromium 131+, where `anchor-scope` keeps each nav's anchors (`--ns-active`,
`--ns-hover`, `--ns-nav`) inside it. On older browsers, a second nav on the same page steals the first one's bubbles.

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
| `placement` | `'top'` \| `'bottom'` | `'top'` | `'top'`: sticky at the top of its container. `'bottom'`: fixed to the bottom of the viewport (clears the iPhone home bar). |
| `align` | `'center'` \| `'start'` | `'center'` | `'start'` pins the pill to the left, so it collapses and expands **left-to-right** instead of from the centre. |
| `icons` | string[] | built-in 6 | SVG markup per link. Only used together with `links`. Falls back to the built-in icons. |
| `collapseWhenIdle` | boolean | `false` | Collapse after `idleMs` without interaction **anywhere** on the page, even above `threshold` or when nothing scrolls. Hover and keyboard focus pause the countdown. |
| `collapsedLabel` | `'icon'` \| `'number'` | `'icon'` | What the collapsed pill shows: the active link's icon, or its position (1, 2, 3…). |
| `holdActions` | `{ label, icon, onSelect }[]` | none | Up to 3. **Press and hold the collapsed icon** and these fan out as round buttons ([hold-fan](../hold-fan/README.md)). A tap still expands the nav. |

**Handle:**

- `setActive(index)` selects a link from code (moves the bubble, updates the collapsed icon) **without**
  calling `onChange`. Returns `false` for an index that doesn't exist.
- `destroy()` removes the nav and its scroll/resize listeners. Call it when removing the nav,
  or listeners pile up each time you remount it.

## When does it collapse?

Only when **`scrollRoot` scrolls down past `threshold`**. If nothing scrolls (a short page, or content that
scrolls inside a container you didn't pass as `scrollRoot`), it stays expanded. That's by design, not a bug.
To check whether the page can scroll, run in the Console:

```js
document.documentElement.scrollHeight - innerHeight   // must be > 40 for window scrolling to collapse it
```

Pressing anywhere outside the nav (a different surface) also collapses it straight away.

## Sizing

Set these on the mount target or any ancestor:

| Variable | Default | Controls |
|---|---|---|
| `--nav-width` | `min(92vw, 560px)` | Max width when expanded |
| `--nav-padding` | `6px` | Shell padding |
| `--nav-link-padding` | `10px 20px` | Per-link padding |
| `--nav-font-size` | `15px` | Link font size |

The nav mounts inside a sticky wrapper (`.nav-selector-fluid-hold-wrap`, `top: 16px`) so it stays
reachable on long pages. It follows the theme, inverted on purpose: in dusk it is the brightest thing on the page (light pill, white halo, dark active bubble); in light it is dark metal with a dark halo and a white bubble. Public variables: `--nav-selector-fluid-hold-{bg,border,shadow,fade,link,link-hover,link-active,bubble-bg,bubble-shadow,hover-bg,hover-shadow,arrow-bg,arrow-ink}`; light values live in `css/global/theme.css`, dusk values are the defaults.

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
- **Anchor names are scoped, not unique.** `anchor-scope` on `.nav-selector-fluid-hold` confines `--ns-active`,
  `--ns-hover` and `--ns-nav` to each nav. If you add another anchor name, add it to that `anchor-scope` list too,
  or two navs on one page will fight over it.
- **Test in the gallery** (`/HTML-pages/components.html`, page 1) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Your page scrolls inside a container, and the nav never collapses

Pass that container as `scrollRoot`:
```js
await mountNavSelector('#nav', { scrollRoot: document.querySelector('main') });
```
Run `el.scrollHeight - el.clientHeight` on the container in the Console: it must be more than `threshold` (40).

### Use it as a bottom page switcher (as the gallery does)

```js
const pages = [{ id: 'home', title: 'Home', icon: '<svg …>' }, /* … */];
const nav = await mountNavSelector('#page-nav', {
    placement: 'bottom',
    align: 'start',                                   // pinned left, grows to the right
    links: pages.map((p) => p.title),
    hrefs: pages.map((p) => '#' + p.id),
    icons: pages.map((p) => p.icon),
    collapseWhenIdle: true,                           // tuck away after 3s idle, even at the top
    idleMs: 3000,
    collapsedLabel: 'number',                         // collapsed pill shows "4" instead of the icon
    onChange: (label, href) => showPage(href),
});
nav.setActive(3);                                     // e.g. after a keyboard shortcut
```
Give the page bottom padding (about `46px + 32px + env(safe-area-inset-bottom)`) so the last content isn't hidden
behind the bar. **Verify:** the gallery itself: its bottom bar is this recipe.

### Collapse sooner or later, or never re-collapse on idle

`threshold` sets how far from the top it stays expanded; `idleMs` sets the re-collapse delay after expanding
mid-page. For "never on idle", pass a very large `idleMs` (for example `1e9`).

### Recolour it

The colours are hard-coded (dark greys, white bubble), not variables. To theme it, turn each colour into a
public variable the way the other components do, for example
`background: linear-gradient(145deg, var(--nav-selector-bg-from, #2a2a2a), var(--nav-selector-bg-to, #111));`,
then add light values to `css/global/theme.css`.

## iplement hybrid (more than three links)

With more than `visible` links (default 3) the pill shows three and hugs them. The arrows sit outside the pill and slide the window one link at a time. A dimmed arrow means nothing further that way; it still takes the click, so it never counts as a tap outside. If you slide away and pick nothing, the pill reopens on the lineup that holds the active link. Live demo: `/HTML-pages/components.html#iplement-hybrid`.
