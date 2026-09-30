package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** What an assigned moderator reports back to the admin. For an appeal it may carry a recommendation; the admin still decides. */
@Entity
@Table(name = "finding", indexes = @Index(name = "idx_finding_investigation", columnList = "investigation_id, created_at"))
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "moderator_id", nullable = false)
    private UUID moderatorId;

    @Column(nullable = false, length = 2000)
    private String text;

    /** STICKS or DROPS, appeals only. */
    @Column(length = 10)
    private String recommendation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Finding() {}
    public Finding(UUID investigationId, UUID moderatorId, String text, String recommendation) {
        this.investigationId = investigationId; this.moderatorId = moderatorId; this.text = text; this.recommendation = recommendation;
    }

    public UUID getId() { return id; }
    public UUID getInvestigationId() { return investigationId; }
    public UUID getModeratorId() { return moderatorId; }
    public String getText() { return text; }
    public String getRecommendation() { return recommendation; }
    public Instant getCreatedAt() { return createdAt; }
}
