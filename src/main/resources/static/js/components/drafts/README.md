# drafts

The **Drafts** dialog on your own profile: every post you saved for later, newest first. **Continue** opens
`/HTML-pages/post.html?draft=<id>`; **Delete** removes the draft and any pictures or videos that were only in it.

```js
import { openDrafts } from '/js/components/drafts/drafts.js';
openDrafts();
```

Uses the dialog and card styles of the applications dialog (`.ap-*`, `.pc-*`), so load `workspace.css` like the profile page does.
Drafts are saved from the post page (`post.html`), with `draftSave` in `js/services/api.js`.
