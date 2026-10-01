package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A co-founder of a post: asked by its author, ACTIVE once they accept. Removing or stepping down deletes the row. */
@Entity
@Table(name = "collaborator", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
public class Collaborator {

    /** FROZEN: opted out of the room and the review, spot kept. DISBANDED: removed with a reason, spot stays open to return. */
    public enum State { INVITED, ACTIVE, DECLINED, FROZEN, DISBANDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private State state = State.INVITED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Why they were frozen or disbanded; null otherwise. */
    @Column(length = 300)
    private String reason;

    public Collaborator() {}
    public Collaborator(UUID postId, UUID userId) { this.postId = postId; this.userId = userId; }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getUserId() { return userId; }
    public State getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public String getReason() { return reason; }
    public void setState(State state) { this.state = state; }
    public void setState(State state, String reason) { this.state = state; this.reason = reason; }
    /** Asking again after a decline or a disband starts a fresh request. */
    public void reinvite() { this.state = State.INVITED; this.reason = null; this.createdAt = Instant.now(); }
}
