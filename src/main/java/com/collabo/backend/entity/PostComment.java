package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A comment on a post. Plain text. A reply points at a top-level comment through parentId; replies nest to any depth. */
@Entity
@Table(name = "post_comment", indexes = {@Index(columnList = "post_id, created_at")})
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 500)
    private String body;

    /** The top-level comment this replies to; null for a top-level comment. */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PostComment() {}
    public PostComment(UUID postId, UUID authorId, String body) {
        this.postId = postId; this.authorId = authorId; this.body = body;
    }
    public PostComment(UUID postId, UUID authorId, String body, UUID parentId) {
        this(postId, authorId, body); this.parentId = parentId;
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public UUID getParentId() { return parentId; }
    public Instant getCreatedAt() { return createdAt; }
}
