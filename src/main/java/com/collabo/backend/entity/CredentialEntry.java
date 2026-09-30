package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of someone's record. Nobody writes their own: entries are recorded by the system when a space
 * forms or a milestone is credited. The same event recorded twice is one entry (unique source + user).
 */
@Entity
@Table(name = "credential_entry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source_type", "source_id", "user_id"}),
        indexes = @Index(columnList = "user_id"))
public class CredentialEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CredentialKind kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 300)
    private String detail = "";

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Promoted by the owner into their feats. */
    @Column(nullable = false)
    private boolean featured;

    public CredentialEntry() {}
    public CredentialEntry(UUID userId, CredentialKind kind, String title, String detail,
                           String sourceType, String sourceId, Instant occurredAt) {
        this.userId = userId; this.kind = kind; this.title = title; this.detail = detail;
        this.sourceType = sourceType; this.sourceId = sourceId; this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public CredentialKind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
}
