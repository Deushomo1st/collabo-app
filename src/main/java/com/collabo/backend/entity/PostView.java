package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Someone other than the author has seen a post, in a feed or by opening it. One per person per post, so the count is people, not refreshes. */
@Entity
@Table(name = "post_view", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PostView() {}
    public PostView(UUID postId, UUID userId) { this.postId = postId; this.userId = userId; }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
