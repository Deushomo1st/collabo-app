package com.collabo.backend.service;

import com.collabo.backend.dto.NotificationDtos.NotificationResponse;
import com.collabo.backend.entity.Notification;
import com.collabo.backend.entity.Notification.Bucket;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.live.LiveSignals;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The notification stream. Services call notify() where the event happens; an action-required notification is tied to a
 * refKey and cleared with resolve(refKey) when the thing it points at is settled.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notifications;
    private final LiveSignals signals;

    public NotificationService(NotificationRepository notifications, LiveSignals signals) { this.notifications = notifications; this.signals = signals; }

    public void notify(UUID userId, Bucket bucket, String title, String body, String link) {
        notifications.save(new Notification(userId, bucket, false, null, title, body, link));
        signals.notification(userId);
    }

    /** Like notify, but a person with an unread line for the same link is not told twice: a busy chat stays one "new messages" line until they look. */
    public void notifyOnce(UUID userId, Bucket bucket, String title, String body, String link) {
        if (!notifications.existsByUserIdAndLinkAndReadFalse(userId, link)) notify(userId, bucket, title, body, link);
    }

    /** Like notify, but the same line (same words, same place) is not repeated while it waits unread: like, unlike, like again stays one line. */
    public void notifyUnlessSame(UUID userId, Bucket bucket, String title, String body, String link) {
        if (!notifications.existsByUserIdAndLinkAndBodyAndReadFalse(userId, link, body)) notify(userId, bucket, title, body, link);
    }

    /** Pinned until resolve(refKey): a payment claim holding a room, a clock running down, a removal awaiting an answer. */
    public void require(UUID userId, String refKey, Bucket bucket, String title, String body, String link) {
        notifications.save(new Notification(userId, bucket, true, refKey, title, body, link));
        signals.notification(userId);
    }

    public void resolve(String refKey) {
        notifications.findByRefKeyAndActionRequiredTrue(refKey).forEach(n -> { n.clearAction(); notifications.save(n); signals.notification(n.getUserId()); });
    }

    /** Action-required first (newest first), then the rest of the chosen filter, newest first. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User me, String filter) {
        Bucket only = filter == null || filter.isBlank() ? null : parse(filter);
        return notifications.findTop100ByUserIdOrderByCreatedAtDesc(me.getId()).stream()
                .filter(n -> n.isActionRequired() || only == null || n.getBucket() == only)
                .sorted(Comparator.comparing((Notification n) -> !n.isActionRequired()))   // stable: newest first is kept inside each group
                .map(NotificationService::present).toList();
    }

    @Transactional(readOnly = true)
    public long unread(User me) { return notifications.countByUserIdAndReadFalse(me.getId()); }

    public void markRead(User me, UUID id) {
        Notification n = notifications.findByIdAndUserId(id, me.getId()).orElseThrow(() -> new ResourceNotFoundException("No such notification."));
        n.markRead();
        notifications.save(n);
        signals.notification(me.getId());   // the same person's other tabs
    }

    /** Looking at the thing a notification points to settles it: whatever was waiting about that link is read. */
    public void readLink(User me, String link) {
        List<Notification> waiting = notifications.findByUserIdAndLinkAndReadFalse(me.getId(), link);
        if (waiting.isEmpty()) return;
        waiting.forEach(n -> { n.markRead(); notifications.save(n); });
        signals.notification(me.getId());
    }

    public void readAll(User me) {
        notifications.findByUserIdAndReadFalse(me.getId()).forEach(n -> { n.markRead(); notifications.save(n); });
        signals.notification(me.getId());
    }

    private static Bucket parse(String filter) {
        try { return Bucket.valueOf(filter.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new InvalidProfileException("Filter by spaces, activity or personal."); }
    }

    private static NotificationResponse present(Notification n) {
        return new NotificationResponse(n.getId(), n.getBucket().name(), n.isActionRequired(), n.getTitle(), n.getBody(), n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
