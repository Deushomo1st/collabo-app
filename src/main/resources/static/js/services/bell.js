// Mounts the notification bell in #bell-slot on any page that has one (signed-in users only).
// Load with: <script type="module" src="/js/services/bell.js"></script>
import { mountNotificationBell } from '/js/components/notification-bell/notification-bell.js';
import { currentUser, notificationsList, notificationRead } from '/js/services/api.js';

const pinned = (n) => n.actionRequired;
const FILTERS = [
    { id: 'all', label: 'All', match: () => true },
    ...['SPACES', 'ACTIVITY', 'PERSONAL'].map((b) => ({ id: b, label: b[0] + b.slice(1).toLowerCase(), match: (n) => pinned(n) || n.bucket === b })),
];

const slot = document.getElementById('bell-slot');
if (slot) {
    currentUser().then((me) => {
        if (!me) return;
        return mountNotificationBell(slot, {
            inline: true,
            filters: FILTERS,
            // the server puts action-required first; the panel filters by bucket but never hides those
            loadItems: notificationsList,
            onRead: (n) => notificationRead(n.id).catch(() => {}),
            onSelect: (n) => { if (n.link) location.href = n.link; },
            isActionable: (n) => n.actionRequired,
        });
    }).catch(() => {});
}
