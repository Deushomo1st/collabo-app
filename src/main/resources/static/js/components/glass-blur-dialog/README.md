# glass-blur-dialog

Layered, auto-stacking glass dialogs: custom content, confirm, and alert.
Ported from SchoolHub `app/modal` (`openGlassModal` / `glassConfirm` / `glassAlert`).

| File | Role |
|---|---|
| `glass-blur-dialog.js` | Loader + API (ES module) |
| `glass-blur-dialog.css` | Styles, loaded once as a global `<link>` |
| `glass-blur-dialog.html` | Dialog shell fragment, fetched once and cached |

## Quick start

```html
<script type="module">
    import { openGlassBlurDialog, glassBlurConfirm, glassBlurAlert, preloadGlassBlurDialog }
        from '/js/components/glass-blur-dialog/glass-blur-dialog.js';

    preloadGlassBlurDialog();                 // optional: warm the cache so the first open is instant

    if (await glassBlurConfirm('Delete this file?', { title: 'Delete file?', okText: 'Delete', danger: true })) {
        // deleted
    }
    await glassBlurAlert('Saved.\nYour changes are live.');
</script>
```

No mount target is needed: every dialog attaches itself to `<body>`.

## API

### `openGlassBlurDialog(opts) → Promise<{ close, panel }>`

| Option | Type | Default | Notes |
|---|---|---|---|
| `html` | string | `''` | Panel content. **Not escaped**: never insert user input without escaping it. |
| `size` | `'sm'` \| `'md'` \| `'lg'` \| `'xl'` | `'md'` | `md` fits content (280–440px). `sm` 380, `lg` 600, `xl` 700 (fixed widths, capped at 92vw). |
| `className` | string | — | Extra classes on the panel. |
| `label` | string | — | Accessible name. Only needed if the content has no `.glass-blur-dialog__title`. |
| `clear` | boolean | `false` | See-through glass with no backdrop. Only honoured for the first open dialog. |
| `dismissable` | boolean | `true` | `false` disables backdrop click **and** Escape. Close it yourself with `close()`. |
| `onClose` | function | — | Called once when the dialog closes, however it closes. |

Returns `panel` (the dialog element, for wiring up your buttons) and `close()`.

### `glassBlurConfirm(message, opts) → Promise<boolean>`

Resolves `true` on OK; `false` on Cancel, Escape, or backdrop click.
Options: `title` (`'Are you sure?'`), `okText` (`'Confirm'`), `cancelText` (`'Cancel'`), `danger`, `clear`, `dismissable`.
Focus starts on **Cancel**, the safe choice.

### `glassBlurAlert(message, opts) → Promise<void>`

Resolves when closed. Line breaks (`\n`) in `message` are kept.
Options: `title` (`'Notice'`), `okText` (`'OK'`), `clear`.

### `preloadGlassBlurDialog() → Promise`

Fetches the CSS and the shell once. Every open calls it internally, so it's only an optimisation.

## Classes for your own content

| Class | Use |
|---|---|
| `.glass-blur-dialog__title` | Heading. Also becomes the dialog's accessible name automatically. |
| `.glass-blur-dialog__text` | Muted body text. Add `--pre` to keep line breaks. |
| `.glass-blur-dialog__actions` | Right-aligned button row. |
| `.glass-blur-dialog__btn` | Primary button. Modifiers: `--ghost`, `--danger`. |
| `.glass-blur-dialog__label`, `.glass-blur-dialog__input` | Form fields. |

Example: a small form dialog.

```js
const dlg = await openGlassBlurDialog({
    size: 'sm',
    html: `<h2 class="glass-blur-dialog__title">Change password</h2>
           <label class="glass-blur-dialog__label" for="pw">New password</label>
           <input class="glass-blur-dialog__input" id="pw" type="password">
           <div class="glass-blur-dialog__actions">
               <button type="button" class="glass-blur-dialog__btn glass-blur-dialog__btn--ghost" data-action="cancel">Cancel</button>
               <button type="button" class="glass-blur-dialog__btn" data-action="save">Save password</button>
           </div>`,
});
dlg.panel.querySelector('[data-action="cancel"]').onclick = dlg.close;
```

## Stacking

Opening a dialog while another is open stacks it on top. Depth is automatic:
dialogs 1–2 frost the page, dialog 3+ uses the deepest frost. Escape closes only the topmost dialog.

## Theming

Defaults are SchoolHub's dark "dusk" values. Because dialogs live on `<body>`, override on `:root`:

```css
:root {
    --glass-blur-dialog-brand: #209EBB;
    --glass-blur-dialog-ink: #023047;
}
```

Public variables: `--glass-blur-dialog-` + `ink`, `muted`, `line`, `brand`, `brand-ink`, `danger`,
`bg`, `bg-frost`, `border`, `sheen`, `sheen-frost2`, `blur`, `blur-frost`, `blur-frost2`,
`backdrop-bg`, `backdrop-bg-frost2`, `backdrop-blur`, `backdrop-blur-frost2`, `shadow`.
Variables starting `--_gbd-` are internal; don't set them.

## Behaviour and accessibility

- `role="dialog"`, `aria-modal="true"`; focus moves into the dialog, Tab is trapped inside it, and focus returns to the trigger on close.
- Respects `prefers-reduced-motion`.
- Stylesheet load is awaited before the first open, so a dialog never appears unstyled.

## Test page

`/HTML-pages/glass-blur-dialog-test.html` exercises every type, stacking, and clear glass.

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.glass-blur-dialog__part`, `.glass-blur-dialog--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Public vs private variables.** Pages set the public `--glass-blur-dialog-*` variables. The component only reads its
  private `--_gbd-*` copies. Never set a private variable from outside.
- **Light theme values go in `css/global/theme.css`**, under `:root[data-theme="light"]`. Dusk is the built-in default.
- **Test in the gallery** (`/HTML-pages/components.html`, page 3) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Change the accent colour of every dialog

1. In the page's CSS (or `css/global/theme.css` for the light theme), set it on `:root`:
   ```css
   :root { --glass-blur-dialog-brand: #6d5dfc; --glass-blur-dialog-brand-ink: #ffffff; }
   ```
   It must be `:root`, not the element you clicked, because dialogs attach to `<body>`.
2. **Verify:** gallery page 3 → **Confirm**. The primary button and focus rings use the new colour.

### Add a new size (for example `2xl` = 900px)

1. In `glass-blur-dialog.css`, next to the other sizes:
   ```css
   .glass-blur-dialog__panel--2xl { --_gbd-width: 900px; width: min(92vw, var(--_gbd-width)); }
   ```
2. No JS change is needed: `size: '2xl'` becomes the class `glass-blur-dialog__panel--2xl` automatically.
3. Add `2xl` to the `size` row of the API table above.
4. **Verify:** in the Console, run
   `(await import('/js/components/glass-blur-dialog/glass-blur-dialog.js')).openGlassBlurDialog({ size: '2xl', html: '<p>wide</p>' })`.

### Build a new dialog type (for example a prompt that returns text)

1. Copy `glassBlurConfirm` in `glass-blur-dialog.js` and rename it `glassBlurPrompt`.
2. In its `html`, add `<input class="glass-blur-dialog__input" data-field>` above the actions.
3. On OK, `settle(dlg.panel.querySelector('[data-field]').value)`. On Cancel, and in `onClose`, `settle(null)`
   (the copied code settles `false`; change both).
4. Export it, and document it under **API** above.
5. **Verify:** add a button for it on gallery page 3 (the `mountDemo` call in `components.html`).

### Make a dialog that can't be dismissed by accident

Pass `dismissable: false`. Backdrop clicks and Escape are then ignored, so you **must** give the user a
button that calls `close()`.
