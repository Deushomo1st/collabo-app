package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Someone who joined a space. The owner is implicit and has no row. ACTIVE, LEFT (may return) or REMOVED (may not). */
@Entity
@Table(name = "space_member", uniqueConstraints = @UniqueConstraint(columnNames = {"space_id", "user_id"}))
public class SpaceMember {

    public enum State { ACTIVE, LEFT, REMOVED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private State state = State.ACTIVE;

    @Column(nullable = false, length = 40)
    private String title = "";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "space_member_permission", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 30)
    private Set<SpacePermission> permissions = EnumSet.noneOf(SpacePermission.class);

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    public SpaceMember() {}
    public SpaceMember(UUID spaceId, UUID userId) { this.spaceId = spaceId; this.userId = userId; }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public UUID getUserId() { return userId; }
    public State getState() { return state; }
    public String getTitle() { return title; }
    public Set<SpacePermission> getPermissions() { return permissions; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setState(State state) { this.state = state; }
    public void setTitle(String title) { this.title = title; }
    public void setPermissions(Set<SpacePermission> p) { this.permissions = p.isEmpty() ? EnumSet.noneOf(SpacePermission.class) : EnumSet.copyOf(p); }
    public void rejoin() { this.state = State.ACTIVE; this.joinedAt = Instant.now(); }
}
