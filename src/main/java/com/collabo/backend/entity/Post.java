package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** An idea posted to The Gaze. applyBy null means the application window is open indefinitely. */
@Entity
@Table(name = "post", indexes = {@Index(columnList = "author_id"), @Index(columnList = "created_at")})
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "apply_by")
    private Instant applyBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Post() {}
    public Post(UUID authorId, String title, String body, Instant applyBy) {
        this.authorId = authorId; this.title = title; this.body = body; this.applyBy = applyBy;
    }

    public UUID getId() { return id; }
    public UUID getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getApplyBy() { return applyBy; }
    public void setApplyBy(Instant applyBy) { this.applyBy = applyBy; }
    public Instant getCreatedAt() { return createdAt; }

    /** pending while the window is open; closed once it ends. ("space formed" arrives with spaces.) */
    public String status() { return applyBy == null || applyBy.isAfter(Instant.now()) ? "pending" : "closed"; }
}
