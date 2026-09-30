package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * What is left when a removal process completes: who removed whom, from which space, why, and whether it carries a badge.
 * The badge sticks only when the space had real work behind it; an appeal can drop it. The space name is copied so the
 * record still reads if the space changes.
 */
@Entity
@Table(name = "removal_record", indexes = @Index(name = "idx_removal_record_removed", columnList = "removed_id, created_at"))
public class RemovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "process_id", nullable = false, unique = true)
    private UUID processId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "space_name", nullable = false, length = 120)
    private String spaceName;

    @Column(name = "removed_id", nullable = false)
    private UUID removedId;

    @Column(name = "removed_by_id", nullable = false)
    private UUID removedById;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false)
    private boolean badge;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RemovalRecord() {}
    public RemovalRecord(UUID processId, UUID spaceId, String spaceName, UUID removedId, UUID removedById, String reason, boolean badge) {
        this.processId = processId; this.spaceId = spaceId; this.spaceName = spaceName; this.removedId = removedId;
        this.removedById = removedById; this.reason = reason; this.badge = badge;
    }

    public UUID getId() { return id; }
    public UUID getProcessId() { return processId; }
    public UUID getSpaceId() { return spaceId; }
    public String getSpaceName() { return spaceName; }
    public UUID getRemovedId() { return removedId; }
    public UUID getRemovedById() { return removedById; }
    public String getReason() { return reason; }
    public boolean isBadge() { return badge; }
    public Instant getCreatedAt() { return createdAt; }
    public void dropBadge() { this.badge = false; }
}
