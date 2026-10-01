# avatar

A round profile picture, and an **upload + crop** dialog (circle overlay) that turns any image into a 512×512 JPEG.
Ported from SchoolHub `app/avatar`, where it powers the profile page's picture.

**Depends on:** [`glass-blur-dialog`](../glass-blur-dialog/README.md) (the upload dialog), imported automatically.

| File | Role |
|---|---|
| `avatar.js` | API (ES module) |
| `avatar.css` | Card + upload styles, loaded once |
| `avatar.html` | `<template data-part="upload">`: the upload dialog's content |

## Quick start: an editable profile picture

```js
import { createAvatarCard, openAvatarUpload } from '/js/components/avatar/avatar.js';

function renderPicture(slot, user) {
    slot.replaceChildren(createAvatarCard({
        src: user.avatar,
        name: `${user.firstName} ${user.lastName}`,
        editable: true,
        onClick: () => openAvatarUpload({
            current: user.avatar,
            onSave: async (dataUrl, blob) => {
                // dataUrl === null means "Remove" was pressed
                await fetch('/api/me/avatar', { method: 'PUT', body: blob ?? '' });
                user.avatar = dataUrl;
                renderPicture(slot, user);
            },
        }),
    }));
}
```

## API

### `createAvatarCard(opts) → HTMLElement`

| Option | Type | Notes |
|---|---|---|
| `src` | string | Image URL or data URL. Missing or broken shows initials (or "+" when editable). |
| `name` | string | Shown under the card (unless `showName: false`) and used for initials. |
| `editable` | boolean | Hover lift, "+" placeholder, and an accessible "Change profile picture" label. |
| `onClick` | function | Makes it a `<button>`. Usually opens `openAvatarUpload`. |
| `showName` | boolean | Default `true`. |

Returns a `<button>` when editable or clickable, otherwise a `<div>`.

### `openAvatarUpload(opts) → Promise<{ close, panel }>`

| Option | Type | Notes |
|---|---|---|
| `current` | string | The existing picture: the dialog starts from it and shows **Remove**. |
| `onSave` | `(dataUrl, blob) => void` | Save: a 512×512 JPEG (quality .85) as both a data URL and a `Blob`. Remove: `(null, null)`. |
| `title` | string | Default `'Profile picture'`. |

**Storing it:** prefer uploading the `Blob` (multipart or raw body) and storing a file URL. SchoolHub stored the
data URL itself in the user record, which works, but puts roughly 40–80 KB of base64 into every user payload.

### `preloadAvatar() → Promise`

Loads the CSS and dialog fragment once. Optional.

## Behaviour and fixes

- Drag the image to reposition; the zoom slider keeps the centre anchored.
- The preview renders at device pixel ratio, so it's sharp on high-DPI screens, and dragging works when the
  crop area is shrunk on narrow screens (pointer movement is rescaled).
- **Fixed from SchoolHub:** re-opening with `current` now actually loads the current picture (the original
  called `loadFile(null)`, which did nothing). Non-image files are rejected with a message.
- A `current` image from another origin can only be re-saved if that server sends CORS headers; otherwise
  the dialog asks the user to choose a file instead.

## Theming

Public variables: `--avatar-` + `brand`, `card`, `ink`, `muted`, `line`, `shadow`, `shadow-raised`, `width` (180px).
The upload dialog lives on `<body>`, so set them on `:root`. Light values live in `css/global/theme.css`.

## Editing this component

Rules that keep it safe to change:

- **Every class starts with the component name** (`.avatar__part`, `.avatar--variant`). Component CSS is a
  global `<link>`, so an unprefixed class can collide with another component. Keyframe names follow the same rule.
- **Public vs private variables.** Pages set the public `--avatar-*` variables. The component only reads its
  private `--_av-*` copies. Never set a private variable from outside.
- **Light theme values go in `css/global/theme.css`**, under `:root[data-theme="light"]`. Dusk is the built-in default.
- **Test in the gallery** (`/HTML-pages/components.html`, page 10) in both themes, then hard-refresh (Ctrl+Shift+R)
  after every change, since browsers cache the CSS and JS.

## Recipes (guided changes)

### Save the picture to your backend as a file

```js
onSave: async (dataUrl, blob) => {
    if (!blob) { await fetch('/api/me/avatar', { method: 'DELETE' }); return; }   // Remove pressed
    const form = new FormData();
    form.append('avatar', blob, 'avatar.jpg');
    const { url } = await (await fetch('/api/me/avatar', { method: 'POST', body: form })).json();
    user.avatar = url;                    // store the URL, not the data URL
},
```

### Change the output size or shape

The crop is set by four constants at the top of `avatar.js`: `VP_W`/`VP_H` (the on-screen frame) and
`OUT_W`/`OUT_H` (the saved image).

The picture is already square and shown as a circle. To change the saved size, edit `OUT_W`/`OUT_H`; to inset the circle in the editor, edit `inset` on `.avatar-upload__ring` (0 = the circle touches the frame).
**Verify:** gallery page 10. Save, then check that the reported size and the card's shape match.

### Accept only certain file types or sizes

In `loadFile()` in `avatar.js`, add checks before reading the file:
```js
if (f.size > 5 * 1024 * 1024) { showError('Please choose an image under 5 MB.'); return; }
```
Also narrow `accept="image/*"` in `avatar.html` (for example `image/png,image/jpeg`).

### Use the card without upload (read-only, as on people lists)

`createAvatarCard({ src, name })` with no `editable` and no `onClick` returns a plain, non-interactive `<div>`.
