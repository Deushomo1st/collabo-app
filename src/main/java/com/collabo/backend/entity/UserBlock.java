package com.collabo.backend.entity;



import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_block", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UserBlock() {}
    public UserBlock(UUID blockerId, UUID blockedId) { this.blockerId = blockerId; this.blockedId = blockedId; }

    public UUID getBlockerId() { return blockerId; }
    public UUID getBlockedId() { return blockedId; }
    public Instant getCreatedAt() { return createdAt; }
}
