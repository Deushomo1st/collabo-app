package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** The room a team works in, born from one post. Its owner is the post's author. */
@Entity
@Table(name = "space", uniqueConstraints = @UniqueConstraint(columnNames = "post_id"))
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    /** How long a quiet member has to respond before a removal process runs out. Hard floor of 48 hours, set by the founder. */
    @Column(name = "response_clock_hours", nullable = false, columnDefinition = "integer default 72")
    private int responseClockHours = 72;

    /** Pleas can be switched off entirely for a team working to an outside deadline. */
    @Column(name = "pleas_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean pleasEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Space() {}
    public Space(UUID postId, UUID ownerId, String name) { this.postId = postId; this.ownerId = ownerId; this.name = name; }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public int getResponseClockHours() { return responseClockHours; }
    public boolean isPleasEnabled() { return pleasEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void rename(String name) { this.name = name; }
    public void setResponseClockHours(int hours) { this.responseClockHours = hours; }
    public void setPleasEnabled(boolean on) { this.pleasEnabled = on; }
}
