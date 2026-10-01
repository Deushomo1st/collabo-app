package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A collaborator flagged as quiet. They have the idea's response clock to answer (a plea buys twelve hours, the founder can cancel).
 * If the clock runs out the case goes to VOTING: the founder and the other collaborators choose to freeze or disband them.
 */
@Entity
@Table(name = "collaborator_case", indexes = @Index(name = "idx_ccase_state_deadline", columnList = "state, deadline"))
public class CollaboratorCase {

    public enum State { RUNNING, VOTING, RESPONDED, CANCELLED, DECIDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private State state = State.RUNNING;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(nullable = false)
    private Instant deadline;

    public CollaboratorCase() {}
    public CollaboratorCase(UUID postId, UUID targetId, UUID initiatorId, String reason, Instant deadline) {
        this.postId = postId; this.targetId = targetId; this.initiatorId = initiatorId; this.reason = reason; this.deadline = deadline;
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getTargetId() { return targetId; }
    public UUID getInitiatorId() { return initiatorId; }
    public String getReason() { return reason; }
    public State getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadline() { return deadline; }
    public boolean isOpen() { return state == State.RUNNING || state == State.VOTING; }

    public void extend(long hours) { this.deadline = deadline.plusSeconds(3600 * hours); }
    public void setState(State state) { this.state = state; }
}
