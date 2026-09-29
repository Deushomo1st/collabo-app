# liquid-glass-modal

A "liquid glass" modal specimen: the page behind it is **refracted** (bent) by an SVG
`feDisplacementMap` filter, softened with blur and saturation, while the modal's own text stays crisp.
Vanilla HTML/CSS/JS port of a Lovable-generated React + Tailwind v4 study.

> **Status: specimen, not yet a general-purpose modal.** Its content is fixed in the HTML fragment, and it
> predates the component conventions (see **Known limitations**). For everyday dialogs use
> [`glass-blur-dialog`](../glass-blur-dialog/README.md); use this one where the refraction effect is the point.

| File | Role |
|---|---|
| `liquid-glass-modal.js` | Loader (ES module) |
| `liquid-glass-modal.css` | Styles, loaded as a global `<link>` |
| `liquid-glass-modal.html` | Modal fragment + the SVG refraction filter |

## Quick start

```html
<button type="button" id="open-glass">Preview</button>
<div id="liquid-glass-modal"></div>

<script type="module">
    import { mountLiquidGlassModal } from '/js/components/liquid-glass-modal/liquid-glass-modal.js';

    const trigger = document.querySelector('#open-glass');
    trigger.addEventListener('click', () => {
        mountLiquidGlassModal('#liquid-glass-modal', { trigger });
    });
</script>
```

Each call **opens** the modal (fetches the fragment and appends it to the target). The modal covers the
viewport (`position: fixed`), so the target can be any element.

## API

### `mountLiquidGlassModal(targetSelector = '#liquid-glass-modal', options) → Promise<void>`

`targetSelector` must be a **selector string** (not an element). Resolves to nothing; there is no handle.

| Option | Type | Notes |
|---|---|---|
| `trigger` | Element | Gets focus back when the modal closes. Ignored if `onClose` is set. |
| `onMount` | `(modalEl) => void` | Called after it opens, with the `<section class="liquid-glass-modal">`. The only way to change content today (see recipes). |
| `onClose` | `() => void` | Called when it closes. **Replaces** the focus return to `trigger`, so return focus yourself if you use it. |

## Behaviour

- Closes on **Escape**, the **✕** button, or a **backdrop** click.
- Locks page scroll while open and restores the previous `overflow` on close.
- Focus starts on ✕ and Tab is trapped inside the modal.
- The window `keydown` listener is removed automatically once the modal leaves the page
  (a `MutationObserver` watches for it).
- Opening and scrim animations are skipped under `prefers-reduced-motion`.

**Browser support:** the refraction (`backdrop-filter: url(#…)`) currently works in **Chromium only**.
Other browsers get the plain blur + saturation fallback through `@supports`. Colours use `oklch()`.

## Theming

Its colours are **global, unprefixed** `:root` variables (a pre-convention leftover). Override them on `:root`:

| Variable | Used for |
|---|---|
| `--glass-foreground` | Title and text |
| `--glass-muted` | Body text, meta line, footer |
| `--glass-surface` / `--glass-highlight` | ✕ button background / hover, code chip |
| `--glass-border` | Modal and button borders, footer rule |
| `--status` | The green status dot |
| `--shadow-glass` | Modal shadow |

It isn't wired into `css/global/theme.css`, so it looks the same in dusk and light.

## Known limitations

1. **Fixed content.** Title ("Liquid Glass"), meta ("Material / 01"), body and footer are hard-coded in the fragment.
2. **Unprefixed globals.** The `:root` variables above and the keyframes `glass-fade-in` / `glass-enter`
   can collide with other code. Every other component prefixes them.
3. **Fixed element ids** (`liquid-glass-title`, `liquid-glass-description`, `liquid-glass-modal-filter`):
   calling it again while it's open duplicates them.
4. **No handle.** You can't close or destroy it from code, and `target` must be a string.
5. **Fetches the fragment on every open** and doesn't wait for the stylesheet, so the first open can flash unstyled.

The recipes below fix each one; they're also backlog item 5 in AGENTS.md.

## Editing this component

- **Every class starts with `liquid-glass-modal`** (`.liquid-glass-modal__part`). Component CSS is a global `<link>`,
  so an unprefixed class can collide. The filter id is prefixed too (`#liquid-glass-modal-filter`).
- **Keep the CSS filter reference and the HTML filter id in sync.** The CSS refers to `url("#liquid-glass-modal-filter")`
  three times (the `@supports` condition and both `backdrop-filter` lines); the fragment defines `<filter id="liquid-glass-modal-filter">`.
- **Test** on gallery page 2 (or `/HTML-pages/liquid-glass-test.html`, whose big background text makes the
  refraction easy to see), and hard-refresh (Ctrl+Shift+R) after every change.

## Recipes (guided changes)

### Change the content (without editing the component)

Use `onMount`:
```js
mountLiquidGlassModal('#liquid-glass-modal', {
    trigger,
    onMount: (modal) => {
        modal.querySelector('.liquid-glass-modal__meta').textContent = 'Release / 02';
        modal.querySelector('.liquid-glass-modal__title').textContent = 'New workspace';
        modal.querySelector('.liquid-glass-modal__body').textContent = 'Your team space is ready.';
    },
});
```
Use `textContent` for anything a user typed.

### Make the refraction stronger, softer, or off

In `liquid-glass-modal.html`, inside `<filter id="liquid-glass-modal-filter">`:

- **Strength:** `scale="52"` (big waves) and `scale="18"` (fine ripple). Lower = subtler; `0` = none.
- **Wave size:** `baseFrequency="0.014 0.022"`. Smaller numbers = larger, smoother waves.
- **Off entirely:** delete the `@supports (backdrop-filter: url(...)) { … }` block in the CSS; the
  plain blur fallback remains.

Verify on `/HTML-pages/liquid-glass-test.html`, where the background text shows the bending clearly.

### Prefix the global variables and keyframes (limitation 2)

1. In `liquid-glass-modal.css`, rename every `--glass-*`, `--status`, `--shadow-glass` to
   `--liquid-glass-modal-*` (for example `--liquid-glass-modal-foreground`), in the `:root` block **and** every `var(…)`.
   Better still, move them from `:root` onto `.liquid-glass-modal__portal` as private `--_lgm-*` copies of
   public `--liquid-glass-modal-*` variables, the way the other components do.
2. Rename `@keyframes glass-fade-in` / `glass-enter` to `liquid-glass-modal-fade-in` / `liquid-glass-modal-enter`,
   and update the two `animation:` lines.
3. Check nothing else used the old names:
   `grep -rn -- '--glass-\|--status\|--shadow-glass\|glass-fade-in\|glass-enter' src/main/resources/static`
4. Verify gallery page 2 looks identical before and after.

### Take content from options (limitation 1)

1. In the fragment, give the meta, title, body and footer elements their existing classes only (no demo text).
2. In `mountLiquidGlassModal`, after inserting the fragment, fill them from `options.meta`, `options.title`,
   `options.body` (via `textContent`), and `options.html` for rich body content (documented as "not escaped").
3. Keep today's demo text as the defaults, so the gallery and test page don't change.
4. Update the API table above.

### Return a handle and allow repeat opens (limitations 3 and 4)

1. Accept an element target: `typeof targetSelector === 'string' ? document.querySelector(targetSelector) : targetSelector`.
2. Before inserting, generate a suffix (`const uid = ++counter;`) and rewrite the title/description ids and
   `aria-labelledby`/`aria-describedby` with it. (Leave the filter id alone and only insert the `<svg>` if
   `#liquid-glass-modal-filter` isn't already in the document.)
3. `return { element: modal, close, destroy: close };`, and document it.

### Open instantly (limitation 5)

Cache the fragment the way glass-blur-dialog does: keep one module-level promise that fetches the HTML and
awaits the stylesheet's `load` event, export `preloadLiquidGlassModal()`, and `await` it at the top of
`mountLiquidGlassModal`.
