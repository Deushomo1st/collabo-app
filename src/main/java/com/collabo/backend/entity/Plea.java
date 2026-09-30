package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** "I think I can reach this person: hold on while I try." Buys twelve hours; a request, not a veto. */
@Entity
@Table(name = "plea", indexes = @Index(name = "idx_plea_space_pleader", columnList = "space_id, pleader_id, created_at"))
public class Plea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "process_id", nullable = false)
    private UUID processId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "pleader_id", nullable = false)
    private UUID pleaderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    public Plea() {}
    public Plea(UUID processId, UUID spaceId, UUID pleaderId, Instant createdAt, Instant endsAt) {
        this.processId = processId; this.spaceId = spaceId; this.pleaderId = pleaderId; this.createdAt = createdAt; this.endsAt = endsAt;
    }

    public UUID getId() { return id; }
    public UUID getProcessId() { return processId; }
    public UUID getSpaceId() { return spaceId; }
    public UUID getPleaderId() { return pleaderId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEndsAt() { return endsAt; }
}
