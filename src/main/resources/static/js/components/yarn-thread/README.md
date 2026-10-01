# yarn-thread

A conversation: bubbles, day-less history with "Load earlier yarns", a composer (Enter sends, Shift+Enter
adds a line) and polling for new yarns. It makes no network calls itself; you pass `load` and `send`
(from `js/services/api.js`). Made for the Yarnspaces page.

| File | Role |
|---|---|
| `yarn-thread.js` | `createYarnThread()` + `preloadYarnThread()` (ES module) |
| `yarn-thread.css` | Styles, loaded once as a global `<link>` |

## Quick start

```js
import { createYarnThread, preloadYarnThread } from '/js/components/yarn-thread/yarn-thread.js';
import { yarnHistory, yarnSend, yarnMarkRead } from '/js/services/api.js';

await preloadYarnThread();
const thread = createYarnThread({
    meId, showNames: true,
    load: (before) => yarnHistory(id, before),   // returns yarns, newest first
    send: (body) => yarnSend(id, body),
    onRead: () => yarnMarkRead(id),
});
host.append(thread.element);
// when leaving the screen:
thread.destroy();
```

## API

### `createYarnThread(options) → { element, refresh(), setDisabledReason(text), destroy() }`

| Option | Type | Default | Notes |
|---|---|---|---|
| `meId` | string | — | Yarns from this id are drawn on the right. |
| `showNames` | boolean | `false` | Show the sender above their yarn (use in groups). |
| `load(before?)` | async fn | — | Returns up to 50 yarns, newest first. `before` is the `at` of the oldest yarn shown. |
| `send(body)` | async fn | — | Returns the created yarn. |
| `onRead()` | fn | — | Called when new yarns arrive while the tab is visible. |
| `avatar(name)` | fn → element | — | A picture drawn beside their yarns. The component never fetches one itself. |
| `avatarOn` | `'every'` \| `'latest'` | `'every'` | `'latest'`: only their newest yarn gets the picture (one-to-one chats); `'every'` is for groups. |
| `pollMs` | number | `6000` | Polling interval. |
| `disabledReason` | string | — | Replaces the composer with this text (pending request, blocked...). |
| `onError(err)` | fn | — | Errors are also shown inline. |

A yarn is `{ id, senderId, sender, kind: 'USER'|'SYSTEM', body, at }`. All text goes in through `textContent`.
Always call `destroy()` when the screen goes away, or it keeps polling.

## Theming

Public variables: `--yarn-thread-ink`, `-muted`, `-mine`, `-mine-ink`, `-theirs`, `-line`, `-accent`.
Light values are in `css/global/theme.css`.

## Editing this component

- Every class starts with `yarn-thread`. Component CSS is a global `<link>`, so unprefixed names can collide.
- Set the public variables from outside; the component reads private `--_yt-*` copies.
- Light values go in `css/global/theme.css` under `:root[data-theme="light"]`.
- The composer is sticky above the bottom nav (`bottom: 96px`); change that if the nav moves.
