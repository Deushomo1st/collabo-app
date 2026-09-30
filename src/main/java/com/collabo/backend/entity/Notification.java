package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One line in someone's notification stream. An action-required one carries a block or a timer and stays pinned until the
 * thing it points at (its refKey) is settled; being read does not clear it.
 */
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id, created_at"),
        @Index(name = "idx_notification_ref", columnList = "ref_key")})
public class Notification {

    public enum Bucket { SPACES, ACTIVITY, PERSONAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Bucket bucket;

    @Column(name = "action_required", nullable = false)
    private boolean actionRequired;

    @Column(name = "ref_key", length = 80)
    private String refKey;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 400)
    private String body;

    @Column(length = 200)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}
    public Notification(UUID userId, Bucket bucket, boolean actionRequired, String refKey, String title, String body, String link) {
        this.userId = userId; this.bucket = bucket; this.actionRequired = actionRequired; this.refKey = refKey;
        this.title = cut(title, 160); this.body = cut(body, 400); this.link = link;
    }

    private static String cut(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max - 1) + "…"; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Bucket getBucket() { return bucket; }
    public boolean isActionRequired() { return actionRequired; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRead() { this.read = true; }
    public void clearAction() { this.actionRequired = false; }
}
