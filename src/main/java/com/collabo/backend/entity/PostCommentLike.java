package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A like on a comment. One per person per comment. postId is kept so a deleted post takes its comment likes with it. */
@Entity
@Table(name = "post_comment_like", uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}),
        indexes = {@Index(columnList = "post_id")})
public class PostCommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "comment_id", nullable = false)
    private UUID commentId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PostCommentLike() {}
    public PostCommentLike(UUID commentId, UUID postId, UUID userId) { this.commentId = commentId; this.postId = postId; this.userId = userId; }

    public UUID getId() { return id; }
    public UUID getCommentId() { return commentId; }
    public UUID getPostId() { return postId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
