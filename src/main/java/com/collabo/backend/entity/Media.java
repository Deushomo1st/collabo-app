package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A picture or video someone uploaded. The bytes live on disk (app.media.dir); this row says whose it is and what it is attached to. */
@Entity
@Table(name = "media", indexes = {@Index(columnList = "owner_id"), @Index(columnList = "post_id")})
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Null until the post it belongs to is published (a draft or an unsent upload). */
    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Media() {}
    public Media(UUID ownerId, String contentType, long sizeBytes) { this.ownerId = ownerId; this.contentType = contentType; this.sizeBytes = sizeBytes; }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public String kind() { return contentType.startsWith("video/") ? "VIDEO" : "IMAGE"; }
}
