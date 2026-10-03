// Small per-tab memory for a page that is left and come back to (a post or report and its settings page).
// sessionStorage can be blocked, so every call is guarded; without it the page just starts fresh.
export const stash = (key, store = 'sessionStorage') => ({
    read: () => { try { return JSON.parse(window[store].getItem(key)); } catch { return null; } },
    write: (v) => { try { window[store].setItem(key, JSON.stringify(v)); } catch { /* private mode: nothing is kept */ } },
    clear: () => { try { window[store].removeItem(key); } catch { /* nothing to clear */ } },
    take() { const v = this.read(); this.clear(); return v; },   // read once, then forget
});

export const postSet = stash('collaboPostSet');       // comments, shout-outs, anonymous, audience
export const postWork = stash('collaboPostWork');     // the text and files while the settings page is open
export const postDefault = stash('collaboPostDefault', 'localStorage');   // Settings > New posts: what every new post starts from (kept on this device)
export const reportSet = stash('collaboReportSet');
export const reportWork = stash('collaboReportWork');

export const DEFAULT_POST_SET = {
    commentsOn: true, shoutsOn: true, anonymous: false, applicationsOn: true,   // applicationsOn false = a regular post
    audience: 'EVERYONE', audienceWith: [],   // what the server is sent
    ui: { kind: 'EVERYONE', mode: 'in', room: null, ticked: [] },   // what the settings page shows
};
