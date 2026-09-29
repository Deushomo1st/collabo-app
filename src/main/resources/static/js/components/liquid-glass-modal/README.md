# liquid-glass-modal

A modal whose backdrop **refracts** (bends) the page behind it through an SVG `feDisplacementMap` filter,
softened with blur and saturation, while the modal's own text stays crisp.
Vanilla HTML/CSS/JS port of a Lovable-generated React + Tailwind v4 study.

For everyday confirms, alerts and forms, [`glass-blur-dialog`](../glass-blur-dialog/README.md) is lighter
and stacks with the rest of the UI. Use this one where the refraction effect is the point.

| File | Role |
|---|---|
| `liquid-glass-modal.js` | Loader + API (ES module) |
| `liquid-glass-modal.css` | Styles, loaded once as a global `<link>` |
| `liquid-glass-modal.html` | Modal fragment (with default content) + the SVG refraction filter |

## Quick start

```html
<button type="button" id="open-glass">What's new</button>

<script type="module">
    import { mountLiquidGlassModal, preloadLiquidGlassModal }
        from '/js/components/liquid-glass-modal/liquid-glass-modal.js';

    preloadLiquidGlassModal();                    // optional: makes the first open instant

    const trigger = document.querySelector('#open-glass');
    trigger.addEventListener('click', () => mountLiquidGlassModal('body', {
        trigger,
        meta: 'Release / 02',
        title: 'New workspace',
        body: 'Your team space is ready.',
        footer: 'Synced just now',
    }));
</script>
```

Each call **opens one modal**. It covers the viewport (`position: fixed`), so the target can be any element.

## API

### `mountLiquidGlassModal(target = '#liquid-glass-modal', options) → Promise<{ element, close, destroy } | undefined>`

`target` is a selector or an element. Resolves to `undefined` if it doesn't exist.

| Option | Type | Notes |
|---|---|---|
| `trigger` | Element | Gets focus back on close. Default: whatever had focus when it opened. |
| `meta` | string | Small uppercase line above the title (default `Material / 01`). |
| `title` | string | Heading; also the dialog's accessible name (default `Liquid Glass`). |
| `body` | string | Body **text** (escaped). |
| `html` | string | Body **HTML**, **not escaped**: never pass user input. Wins over `body`. |
| `footer` | string | Footer text next to the status dot (default `Refraction active`). |
| `onMount` | `(modalEl) => void` | Called after it opens, with the `<section class="liquid-glass-modal">`. |
| `onClose` | `() => void` | Called once when it closes (focus is still returned afterwards). |

Options you leave out keep the fragment's default text.

**Handle:** `close()` closes it (runs `onClose`, returns focus). `destroy()` is the same, for consistency with
the other components. `element` is the dialog `<section>`.

### `preloadLiquidGlassModal() → Promise`

Fetches the fragment and waits for the stylesheet once. Every open calls it internally.

## Behaviour

- Closes on **Escape** (topmost modal only), the **✕** button, or a **backdrop** click.
- Locks page scroll while any modal is open; restores it when the last one closes.
- Focus starts on ✕, Tab is trapped inside, and focus returns on close.
- Each open gets its own element ids, so opening it twice (or stacking two) stays valid.
- The SVG filter is added to the document once and shared by every open.
- If a page removes the modal itself (for example by clearing its container), scroll lock and bookkeeping
  are still cleaned up.
- Opening and scrim animations are skipped under `prefers-reduced-motion`.

**Browser support:** the refraction (`backdrop-filter: url(#…)`) currently works in **Chromium only**.
Other browsers get the plain blur + saturation fallback through `@supports`. Colours use `oklch()`.

## Theming

Public variables (set on `:root` or any ancestor of the target):

| Variable | Used for |
|---|---|
| `--liquid-glass-modal-foreground` | Title and text |
| `--liquid-glass-modal-muted` | Body text, meta line, footer |
| `--liquid-glass-modal-surface` / `-highlight` | ✕ button background / hover, code chip |
| `--liquid-glass-modal-border` | Modal and button borders, footer rule |
| `--liquid-glass-modal-status` | The status dot |
| `--liquid-glass-modal-shadow` | Modal shadow |
| `--liquid-glass-modal-z` | Stacking order (default 50) |

It looks the same in dusk and light (it isn't in `css/global/theme.css`); the refraction reads best over a
dark, busy background either way.

## Editing this component

- **Every class starts with `liquid-glass-modal`** (`.liquid-glass-modal__part`), and so do the keyframes
  (`liquid-glass-modal-fade-in`, `liquid-glass-modal-enter`). Component CSS is a global `<link>`, so an unprefixed
  name can collide with another component.
- **Public vs private variables.** Pages set `--liquid-glass-modal-*`; the component only reads its private
  `--_lgm-*` copies (defined on `.liquid-glass-modal__portal`). Never set a private variable from outside.
- **Keep the filter id in sync.** The CSS refers to `url("#liquid-glass-modal-filter")` three times (the `@supports`
  condition and both `backdrop-filter` lines); the fragment defines `<filter id="liquid-glass-modal-filter">`,
  and the JS looks it up by that id.
- **Don't put ids back in the fragment** (other than the filter): the loader assigns them per open.
- **Test** on gallery page 2, or `/HTML-pages/liquid-glass-test.html` (its big background text shows the
  refraction clearly), and hard-refresh (Ctrl+Shift+R) after every change.

## Recipes (guided changes)

### Show your own content

Pass `meta`, `title`, `body` (or `html`) and `footer`. For content that needs markup:
```js
mountLiquidGlassModal('body', {
    title: 'Keyboard shortcuts',
    html: '<p>Press <code class="liquid-glass-modal__code">Ctrl K</code> to search.</p>',
});
```
Use `body` (not `html`) for anything a user typed.

### Make the refraction stronger, softer, or off

In `liquid-glass-modal.html`, inside `<filter id="liquid-glass-modal-filter">`:

- **Strength:** `scale="52"` (big waves) and `scale="18"` (fine ripple). Lower = subtler; `0` = none.
- **Wave size:** `baseFrequency="0.014 0.022"`. Smaller numbers = larger, smoother waves.
- **Off entirely:** delete the `@supports (backdrop-filter: url(...)) { … }` block in the CSS; the plain blur
  fallback remains.

Verify on `/HTML-pages/liquid-glass-test.html`.

### Recolour it for one page

```css
:root {
    --liquid-glass-modal-status: oklch(0.8 0.15 60);        /* amber dot */
    --liquid-glass-modal-border: oklch(1 0 0 / 0.35);        /* brighter edges */
}
```

### Make it follow the light theme

1. Pick light values for the public variables above (dark text, since the glass will sit over light pages).
2. Add a `liquid-glass-modal` block to `css/global/theme.css` under `:root[data-theme="light"]`.
3. **Verify:** gallery page 2 with the header switcher on Light.

### Add a primary action button to the footer

1. In the fragment, add `<button type="button" class="liquid-glass-modal__action" hidden></button>` at the end
   of `.liquid-glass-modal__footer`.
2. In `liquid-glass-modal.css`, style `.liquid-glass-modal__action` (push it right with `margin-left: auto`).
3. In `mountLiquidGlassModal`, if `options.action` (`{ label, onClick }`) is given, set its `textContent`,
   un-hide it, and call `onClick` then `close()` on click.
4. Document `action` in the API table above.
