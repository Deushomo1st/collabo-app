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
| `mountThemeSwitcher(target, { inline, onChange })` | Mounts a switcher. Returns `{ element, destroy }`. `inline` puts it in normal flow instead of fixed bottom-right. |
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
