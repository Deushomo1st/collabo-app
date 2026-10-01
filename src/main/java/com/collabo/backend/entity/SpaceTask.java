package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A piece of work in a Workspace: who does it and where it stands. Tasks belong to the team, not to the public record. */
@Entity
@Table(name = "space_task", indexes = @Index(name = "idx_space_task_space", columnList = "space_id, created_at"))
public class SpaceTask {

    public enum Status { TODO, DOING, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(nullable = false, length = 140)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.TODO;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "done_at")
    private Instant doneAt;

    public SpaceTask() {}
    public SpaceTask(UUID spaceId, String title, UUID createdBy, UUID assigneeId) {
        this.spaceId = spaceId; this.title = title; this.createdBy = createdBy; this.assigneeId = assigneeId;
    }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public String getTitle() { return title; }
    public Status getStatus() { return status; }
    public UUID getAssigneeId() { return assigneeId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDoneAt() { return doneAt; }
    public void setAssigneeId(UUID assigneeId) { this.assigneeId = assigneeId; }
    public void setStatus(Status next) { this.status = next; this.doneAt = next == Status.DONE ? Instant.now() : null; }
}
