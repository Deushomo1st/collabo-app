# theme-switcher

A pill toggle between **dusk** (dark, the default) and **light**. It sets `<html data-theme>`,
saves the choice in `localStorage`, and keeps every switcher on the page in sync.
Ported from SchoolHub (`app.js` theme block, `style.css` `.theme-toggle`).

What each theme *looks like* is decided by `css/global/theme.css`, not by this component.

| File | Role |
|---|---|
| `theme-switcher.js` | Loader + API + theme helpers (ES module) |
| `theme-switcher.css` | Styles, loaded once |
| `theme-switcher.html` | Button fragment |

## Quick start

1. **In `<head>`, right after `global.css`**, apply the saved theme before the first paint, so a light-theme
   user doesn't see a flash of dusk:

   ```html
   <script>try{var t=localStorage.getItem('collaboTheme');if(t)document.documentElement.dataset.theme=t}catch(e){}</script>
   ```

2. Mount the switcher:

   ```js
   import { mountThemeSwitcher } from '/js/components/theme-switcher/theme-switcher.js';
   await mountThemeSwitcher('body');          // fixed bottom-right
   ```

## API

| Export | Does |
|---|---|
| `mountThemeSwitcher(target, { inline, onChange })` | Mounts a switcher. Returns `{ element, destroy }`. `inline` puts it in normal flow instead of fixed bottom-right; an inline one turns into a gear (Settings dialog) at 560px and below. `plain` keeps it as a plain switcher (used inside that dialog). |
| `getTheme()` | `'light'` or `'dusk'` |
| `setTheme(theme, { persist = true })` | Applies a theme, saves it, and fires `collabo:themechange` |
| `initTheme()` | Applies the saved theme if the page hasn't set one. Called by `mountThemeSwitcher`. |
| `THEME_STORAGE_KEY` | `'collaboTheme'` |
| `THEME_EVENT` | `'collabo:themechange'`, with `event.detail.theme` |

To react to theme changes elsewhere:

```js
document.addEventListener('collabo:themechange', (e) => console.log(e.detail.theme));
```

Change the theme through `setTheme()`, not by editing `data-theme` directly, or the switchers won't update.

## Notes

- SchoolHub's CSS defined `.theme-toggle` twice; this port uses the second (redesigned) block only.
  The first block's leftovers made the dusk pill near-black and changed its hover shadow by accident.
- The switcher colours itself from its own state, so it needs nothing in `theme.css`.
- Public variables: `--theme-switcher-z` (60).

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.theme-switcher__part`, `.theme-switcher--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Its colours follow its own state** (`.theme-switcher--light`), not theme variables, so it needs nothing in
  `css/global/theme.css`. The only public variable is `--theme-switcher-z`.
- **Test in the gallery** (`/HTML-pages/components.html`, page 8) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Make a component respond to the theme

The switcher only flips `<html data-theme>`. For a component to change with it:

1. The component must read **public** variables, for example `var(--my-thing-ink, #F4EDE4)`, where the
   fallback is its dusk value.
2. Add the light values to `css/global/theme.css` under `:root[data-theme="light"]`.
3. **Verify:** toggle the switcher on the component's gallery page.

### Run code when the theme changes

```js
document.addEventListener('collabo:themechange', (e) => chart.setDark(e.detail.theme === 'dusk'));
```

### Follow the operating system's theme until the user chooses

In `initTheme()` in `theme-switcher.js`, when nothing is saved, fall back to the system setting:
```js
if (!saved && matchMedia('(prefers-color-scheme: light)').matches) saved = 'light';
```
Do the same in the no-flash `<head>` snippet on each page, or users will see a flash before the module runs.

### Add a third theme

1. In `theme-switcher.js`, `getTheme`/`setTheme` accept only `light` and `dusk`; widen them to your new name.
2. Replace the two-state toggle in `update()` and the click handler with a cycle through the themes.
3. Add a `:root[data-theme="your-theme"]` block to `css/global/theme.css`.
