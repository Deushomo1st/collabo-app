// avatar: a round profile picture, plus an upload dialog that crops inside a circle.
// Ported from SchoolHub app/avatar (avatarCard / openAvatarUpload).
// Depends on glass-blur-dialog (the upload dialog opens inside one).
//
// Usage:
//   import { createAvatarCard, openAvatarUpload } from '/js/components/avatar/avatar.js';
//
//   const card = createAvatarCard({ src: user.avatar, name: 'Ada Obi', editable: true,
//       onClick: () => openAvatarUpload({
//           current: user.avatar,
//           onSave: async (dataUrl, blob) => { /* upload blob (or dataUrl); null = removed */ },
//       }),
//   });
//   document.querySelector('#profile').appendChild(card);

import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';

const BASE = '/js/components/avatar/avatar';
const VP_W = 320, VP_H = 320;     // crop viewport (square; the circle overlay shows what stays)
const OUT_W = 512, OUT_H = 512;   // saved image (square; shown as a circle)
const ICON_PLUS = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14"/><path d="M12 5v14"/></svg>';

let fragmentPromise = null;

export function preloadAvatar() {
    if (!fragmentPromise) {
        fragmentPromise = Promise.all([
            loadStylesOnce(BASE + '.css', 'avatar'),
            fetch(BASE + '.html').then((res) => {
                if (!res.ok) throw new Error(`avatar: fragment ${res.status}`);
                return res.text();
            }),
        ]).then(([, html]) => {
            const tpl = document.createElement('template');
            tpl.innerHTML = html;
            return tpl.content.querySelector('template[data-part="upload"]').innerHTML;
        }).catch((err) => {
            fragmentPromise = null;
            throw err;
        });
    }
    return fragmentPromise;
}

// opts: { src, name, editable, onClick, showName (default true) }
// Returns an element: a <button> when editable or clickable, otherwise a <div>.
// With no src it shows the person's initials, or a "+" when editable.
export function createAvatarCard(opts = {}) {
    loadStylesOnce(BASE + '.css', 'avatar').catch(() => {});
    const interactive = !!(opts.editable || opts.onClick);
    const card = document.createElement(interactive ? 'button' : 'div');
    card.className = 'avatar-card' + (opts.editable ? ' avatar-card--editable' : '');
    if (interactive) {
        card.type = 'button';
        card.setAttribute('aria-label', opts.editable
            ? `Change profile picture${opts.name ? ` for ${opts.name}` : ''}`
            : (opts.name || 'Profile picture'));
        if (typeof opts.onClick === 'function') card.addEventListener('click', opts.onClick);
    }

    const face = document.createElement('span');
    face.className = 'avatar-card__face';
    const fallback = () => {
        const f = document.createElement('span');
        f.className = 'avatar-card__fallback';
        if (opts.editable) {
            f.innerHTML = ICON_PLUS;
        } else {
            const i = document.createElement('span');
            i.className = 'avatar-card__initials';
            i.textContent = initials(opts.name);
            f.appendChild(i);
        }
        return f;
    };
    if (opts.src) {
        const img = document.createElement('img');
        img.className = 'avatar-card__img';
        img.src = opts.src;
        img.alt = interactive ? '' : (opts.name || 'Profile picture');
        img.draggable = false;
        img.addEventListener('error', () => img.replaceWith(fallback()), { once: true });
        face.appendChild(img);
    } else {
        face.appendChild(fallback());
    }
    card.appendChild(face);

    if (opts.name && opts.showName !== false) {
        const n = document.createElement('span');
        n.className = 'avatar-card__name';
        n.textContent = opts.name;
        card.appendChild(n);
    }
    return card;
}

// Upload + crop dialog.
// opts: { current (URL or data URL), title, onSave(dataUrl, blob) }
//   Save   -> onSave('data:image/jpeg…', Blob)   (512x512 JPEG, quality .85)
//   Remove -> onSave(null, null)                   (only shown when `current` is set)
// Returns the dialog handle { close, panel }.
export async function openAvatarUpload(opts = {}) {
    const html = await preloadAvatar();
    const dlg = await openGlassBlurDialog({ className: 'avatar-upload', html });
    const panel = dlg.panel;
    if (opts.title) panel.querySelector('.glass-blur-dialog__title').textContent = opts.title;

    const canvas = panel.querySelector('.avatar-upload__canvas');
    const ctx = canvas.getContext('2d');
    const fileEl = panel.querySelector('.avatar-upload__file');
    const zoomEl = panel.querySelector('.avatar-upload__zoom');
    const emptyEl = panel.querySelector('.avatar-upload__empty');
    const msgEl = panel.querySelector('.avatar-upload__msg');
    const saveBtn = panel.querySelector('[data-act="save"]');
    const removeBtn = panel.querySelector('[data-act="remove"]');

    // Sharp preview on high-DPI screens: bigger backing store, same drawing units.
    const dpr = window.devicePixelRatio || 1;
    canvas.width = VP_W * dpr;
    canvas.height = VP_H * dpr;
    ctx.scale(dpr, dpr);

    let img = null, zoom = 1, base = 1, off = { x: 0, y: 0 }, drag = null;

    function showError(text) { msgEl.textContent = text; msgEl.hidden = !text; }
    function clamp() {
        const dw = img.naturalWidth * base * zoom, dh = img.naturalHeight * base * zoom;
        off.x = Math.min(0, Math.max(VP_W - dw, off.x));
        off.y = Math.min(0, Math.max(VP_H - dh, off.y));
    }
    function draw() {
        ctx.clearRect(0, 0, VP_W, VP_H);
        if (!img) return;
        ctx.drawImage(img, off.x, off.y, img.naturalWidth * base * zoom, img.naturalHeight * base * zoom);
    }
    function useImage(im) {
        img = im;
        base = Math.max(VP_W / im.naturalWidth, VP_H / im.naturalHeight);       // cover the frame
        zoom = 1;
        off = { x: (VP_W - im.naturalWidth * base) / 2, y: (VP_H - im.naturalHeight * base) / 2 };
        zoomEl.value = 1;
        zoomEl.disabled = false;
        saveBtn.disabled = false;
        emptyEl.hidden = true;
        showError('');
        draw();
    }
    function loadSrc(src) {
        const im = new Image();
        if (/^https?:/i.test(src) && new URL(src, location.href).origin !== location.origin) {
            im.crossOrigin = 'anonymous';   // needed to export it again; the server must allow CORS
        }
        im.onload = () => useImage(im);
        im.onerror = () => showError('That image could not be loaded.');
        im.src = src;
    }
    function loadFile(f) {
        if (!f) return;
        if (!f.type.startsWith('image/')) { showError('Please choose an image file.'); return; }
        const r = new FileReader();
        r.onload = () => loadSrc(r.result);
        r.readAsDataURL(f);
    }

    // Drag to reposition. The canvas may be drawn smaller than 320px on narrow screens,
    // so pointer movement is scaled from CSS pixels to crop-viewport units.
    canvas.addEventListener('pointerdown', (ev) => {
        if (!img) return;
        const k = VP_W / canvas.getBoundingClientRect().width;
        drag = { x: ev.clientX, y: ev.clientY, ox: off.x, oy: off.y, k };
        canvas.setPointerCapture(ev.pointerId);
    });
    canvas.addEventListener('pointermove', (ev) => {
        if (!drag) return;
        off.x = drag.ox + (ev.clientX - drag.x) * drag.k;
        off.y = drag.oy + (ev.clientY - drag.y) * drag.k;
        clamp();
        draw();
    });
    const endDrag = () => { drag = null; };
    canvas.addEventListener('pointerup', endDrag);
    canvas.addEventListener('pointercancel', endDrag);

    zoomEl.addEventListener('input', () => {
        if (!img) return;
        const cx = VP_W / 2, cy = VP_H / 2, prev = zoom;
        zoom = parseFloat(zoomEl.value);
        off.x = cx - (cx - off.x) * (zoom / prev);     // keep the centre anchored while zooming
        off.y = cy - (cy - off.y) * (zoom / prev);
        clamp();
        draw();
    });

    panel.querySelector('[data-act="pick"]').onclick = () => fileEl.click();
    fileEl.onchange = () => loadFile(fileEl.files[0]);
    panel.querySelector('[data-act="cancel"]').onclick = dlg.close;

    if (opts.current) {
        removeBtn.hidden = false;
        removeBtn.onclick = () => {
            if (typeof opts.onSave === 'function') opts.onSave(null, null);
            dlg.close();
        };
        loadSrc(opts.current);            // start from the current picture
    }

    saveBtn.onclick = () => {
        if (!img) return;
        const out = document.createElement('canvas');
        out.width = OUT_W;
        out.height = OUT_H;
        const s = OUT_W / VP_W;
        out.getContext('2d').drawImage(img, off.x * s, off.y * s,
            img.naturalWidth * base * zoom * s, img.naturalHeight * base * zoom * s);
        let dataUrl;
        try {
            dataUrl = out.toDataURL('image/jpeg', 0.85);
        } catch (e) {
            showError('This image is on another site that does not allow re-saving it. Choose a file instead.');
            return;
        }
        saveBtn.disabled = true;
        out.toBlob((blob) => {
            if (typeof opts.onSave === 'function') opts.onSave(dataUrl, blob);
            dlg.close();
        }, 'image/jpeg', 0.85);
    };

    return dlg;
}

function initials(name) {
    const p = String(name || '').trim().split(/\s+/);
    return (((p[0] || '')[0] || '') + ((p[1] || '')[0] || '')).toUpperCase() || '?';
}

function loadStylesOnce(href, componentName) {
    const existing = document.querySelector(`link[data-component="${componentName}"]`);
    if (existing) return existing.sheet ? Promise.resolve() : new Promise((r) => existing.addEventListener('load', r, { once: true }));
    return new Promise((resolve, reject) => {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = href;
        link.dataset.component = componentName;
        link.onload = resolve;
        link.onerror = () => reject(new Error(`${componentName}: stylesheet failed to load`));
        document.head.appendChild(link);
    });
}
