package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A shout-out: a repost of someone's idea to your own network. One per person per post. */
@Entity
@Table(name = "shout", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}),
        indexes = {@Index(columnList = "user_id, created_at")})
public class Shout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Shout() {}
    public Shout(UUID postId, UUID userId) { this.postId = postId; this.userId = userId; }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
