package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A moderator looking into one Yarnspace (a MySpace, WeSpace or Workspace thread). It starts when a member reports the thread, or
 * when a removed person appeals a termination. The admin assigns the moderator and closes it; the moderator only reports back.
 */
@Entity
@Table(name = "investigation", indexes = @Index(name = "idx_investigation_status", columnList = "status, created_at"))
public class Investigation {

    public enum Kind { REPORT, APPEAL }
    public enum Status { OPEN, ASSIGNED, REPORTED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 10) @Enumerated(EnumType.STRING)
    private Kind kind;

    @Column(nullable = false, length = 10) @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    /** The Yarnspace under investigation. Null only for an appeal whose space never had a room. */
    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(nullable = false, length = 1000)
    private String reason;

    /** Set for an appeal: the appeal this investigation rules on. */
    @Column(name = "appeal_id", unique = true)
    private UUID appealId;

    /** An appeal reads only this window of the room; null means the whole thread. */
    @Column(name = "window_from")
    private Instant windowFrom;
    @Column(name = "window_to")
    private Instant windowTo;

    @Column(name = "moderator_id")
    private UUID moderatorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "assigned_at")
    private Instant assignedAt;

    public Investigation() {}
    public Investigation(Kind kind, UUID threadId, UUID reporterId, String reason) {
        this.kind = kind; this.threadId = threadId; this.reporterId = reporterId; this.reason = reason;
    }

    public static Investigation forAppeal(UUID threadId, Appeal a, Instant from, Instant to) {
        Investigation i = new Investigation(Kind.APPEAL, threadId, a.getAppellantId(), a.getNote());
        i.appealId = a.getId(); i.windowFrom = from; i.windowTo = to;
        return i;
    }

    public UUID getId() { return id; }
    public Kind getKind() { return kind; }
    public Status getStatus() { return status; }
    public UUID getThreadId() { return threadId; }
    public UUID getReporterId() { return reporterId; }
    public String getReason() { return reason; }
    public UUID getAppealId() { return appealId; }
    public Instant getWindowFrom() { return windowFrom; }
    public Instant getWindowTo() { return windowTo; }
    public UUID getModeratorId() { return moderatorId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAssignedAt() { return assignedAt; }

    public void assign(UUID moderator) { this.moderatorId = moderator; this.assignedAt = Instant.now(); this.status = Status.ASSIGNED; }
    public void close() { this.status = Status.CLOSED; }
    public boolean isClosed() { return status == Status.CLOSED; }
}
