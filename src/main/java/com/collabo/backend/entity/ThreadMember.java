package com.collabo.backend.entity;



import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One person's seat in a thread. Archive / pin / mute live here so they are per person. */
@Entity
@Table(name = "thread_member", uniqueConstraints = @UniqueConstraint(columnNames = {"thread_id", "user_id"}))
public class ThreadMember {

    public enum Role { OWNER, MEMBER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false) @Enumerated(EnumType.STRING)
    private Role role = Role.MEMBER;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt = Instant.EPOCH;

    /** Delivered watermark: the newest yarn whose arrival this person's browser confirmed. Nullable so old rows load (null = never). */
    @Column(name = "last_delivered_at")
    private Instant lastDeliveredAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    private boolean pinned;
    private boolean muted;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    public ThreadMember() {}
    public ThreadMember(UUID threadId, UUID userId, Role role) {
        this.threadId = threadId; this.userId = userId; this.role = role;
    }

    public UUID getId() { return id; }
    public UUID getThreadId() { return threadId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }
    public Instant getLastDeliveredAt() { return lastDeliveredAt == null ? Instant.EPOCH : lastDeliveredAt; }
    /** Watermarks only move forward. True when this call advanced it. */
    public boolean deliveredUpTo(Instant at) {
        if (!at.isAfter(getLastDeliveredAt())) return false;
        this.lastDeliveredAt = at;
        return true;
    }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public boolean isArchived() { return archivedAt != null; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
}
