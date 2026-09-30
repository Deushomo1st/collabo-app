package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A member flagged as quiet. They have the space's response clock to answer; a plea buys twelve hours; the founder can
 * cancel at any moment. If the time runs out untouched, the member is removed.
 */
@Entity
@Table(name = "removal_process", indexes = @Index(name = "idx_removal_state_deadline", columnList = "state, deadline"))
public class RemovalProcess {

    public enum State { RUNNING, RESPONDED, CANCELLED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

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

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public RemovalProcess() {}
    public RemovalProcess(UUID spaceId, UUID targetId, UUID initiatorId, String reason, Instant deadline) {
        this.spaceId = spaceId; this.targetId = targetId; this.initiatorId = initiatorId; this.reason = reason; this.deadline = deadline;
    }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public UUID getTargetId() { return targetId; }
    public UUID getInitiatorId() { return initiatorId; }
    public String getReason() { return reason; }
    public State getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadline() { return deadline; }
    public Instant getResolvedAt() { return resolvedAt; }
    public boolean isRunning() { return state == State.RUNNING; }

    public void extend(long hours) { this.deadline = deadline.plusSeconds(3600 * hours); }
    public void resolve(State next) { this.state = next; this.resolvedAt = Instant.now(); }
}
