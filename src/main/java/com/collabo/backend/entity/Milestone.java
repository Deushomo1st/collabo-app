package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** An objective of a space. Fulfilling it (with a note) stamps and credits the members who are in the room at that moment. */
@Entity
@Table(name = "milestone", indexes = @Index(name = "idx_milestone_space", columnList = "space_id, created_at"))
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String note = "";

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    public Milestone() {}
    public Milestone(UUID spaceId, String title, UUID createdBy) { this.spaceId = spaceId; this.title = title; this.createdBy = createdBy; }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFulfilledAt() { return fulfilledAt; }
    public boolean isFulfilled() { return fulfilledAt != null; }
    public void fulfil(String note) { this.note = note; this.fulfilledAt = Instant.now(); }
}
