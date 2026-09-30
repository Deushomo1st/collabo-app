package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** The removed person reporting a termination, once per record. A moderator decides whether the badge sticks; the removal itself stands. */
@Entity
@Table(name = "appeal", indexes = @Index(name = "idx_appeal_open", columnList = "outcome, created_at"))
public class Appeal {

    public enum Outcome { STICKS, DROPS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "record_id", nullable = false, unique = true)
    private UUID recordId;

    @Column(name = "appellant_id", nullable = false)
    private UUID appellantId;

    @Column(nullable = false, length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Outcome outcome;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Appeal() {}
    public Appeal(UUID recordId, UUID appellantId, String note) { this.recordId = recordId; this.appellantId = appellantId; this.note = note; }

    public UUID getId() { return id; }
    public UUID getRecordId() { return recordId; }
    public UUID getAppellantId() { return appellantId; }
    public String getNote() { return note; }
    public Outcome getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public boolean isOpen() { return outcome == null; }
    public void decide(Outcome outcome, UUID by) { this.outcome = outcome; this.decidedBy = by; this.decidedAt = Instant.now(); }
}
