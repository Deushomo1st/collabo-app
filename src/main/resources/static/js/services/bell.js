// Mounts the notification bell in #bell-slot on any page that has one (signed-in users only).
// Load with: <script type="module" src="/js/services/bell.js"></script>
import { mountNotificationBell } from '/js/components/notification-bell/notification-bell.js';
import { currentUser, notificationsList, notificationRead } from '/js/services/api.js';
import { collaborationsInto } from '/js/components/collaborators/collaborators.js';
import { live } from '/js/services/live.js';

const pinned = (n) => n.actionRequired;
const FILTERS = [
    { id: 'all', label: 'All', match: () => true },
    ...['SPACES', 'ACTIVITY', 'PERSONAL'].map((b) => ({ id: b, label: b[0] + b.slice(1).toLowerCase(), match: (n) => pinned(n) || n.bucket === b })),
    { id: 'COLLABS', label: 'Collaborations', view: collaborationsInto },   // requests to collaborate, with Accept / Decline
];

const slot = document.getElementById('bell-slot');
if (slot) {
    currentUser().then((me) => {
        if (!me) return;
        return mountNotificationBell(slot, {
            inline: true,
            filters: FILTERS,
            footer: { label: 'See all notifications', onClick: () => { location.href = '/HTML-pages/notifications.html'; } },
            // the server puts action-required first; the panel filters by bucket but never hides those
            loadItems: notificationsList,
            onRead: (n) => notificationRead(n.id).catch(() => {}),
            onSelect: (n) => { if (n.link) location.href = n.link; },
            isActionable: (n) => n.actionRequired,
        }).then((bell) => {
            // The server says when something changed (a new notification, one settled, one read in another tab), so the bell
            // looks again at once. The 60s poll underneath stays as the net.
            live.on('notification', () => bell.refresh().catch(() => {}));
            live.onResync(() => bell.refresh().catch(() => {}));
        });
    }).catch(() => {});
}
