package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** follower -> followed. One row per pair; nobody follows themselves (the service refuses). */
@Entity
@Table(name = "follow", uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "followed_id"}),
        indexes = @Index(columnList = "followed_id"))
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "follower_id", nullable = false)
    private UUID followerId;

    @Column(name = "followed_id", nullable = false)
    private UUID followedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Follow() {}
    public Follow(UUID followerId, UUID followedId) { this.followerId = followerId; this.followedId = followedId; }

    public UUID getFollowerId() { return followerId; }
    public UUID getFollowedId() { return followedId; }
}
