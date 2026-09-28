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
