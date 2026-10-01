// Minimal service worker: it exists so the browser offers "Install app". Every request still goes to the network,
// so a deploy is never hidden behind a stale cache. // ponytail: add an offline page here if you want one.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', () => {});
