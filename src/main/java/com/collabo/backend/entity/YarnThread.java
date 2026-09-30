package com.collabo.backend.entity;



import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A conversation. MySpace = one-to-one, WeSpace = one-to-many, Workspace = work group. */
@Entity
@Table(name = "yarn_thread")
public class YarnThread {

    public enum Tier { MYSPACE, WESPACE, WORKSPACE }
    public enum Status { PENDING, ACCEPTED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) @Enumerated(EnumType.STRING)
    private Tier tier;

    private String name; // null for MySpace: the UI shows the other person

    @Column(nullable = false) @Enumerated(EnumType.STRING)
    private Status status = Status.ACCEPTED;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** Sorted "idA:idB" for MySpace; UNIQUE so a pair only ever has one thread. Null for groups. */
    @Column(name = "dm_key", unique = true)
    private String dmKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_yarn_at", nullable = false)
    private Instant lastYarnAt = Instant.now();

    @Column(name = "last_sender_id")
    private UUID lastSenderId;

    @Column(name = "last_body", length = 160)
    private String lastBody;

    public UUID getId() { return id; }
    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = tier; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public String getDmKey() { return dmKey; }
    public void setDmKey(String dmKey) { this.dmKey = dmKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastYarnAt() { return lastYarnAt; }
    public UUID getLastSenderId() { return lastSenderId; }
    public String getLastBody() { return lastBody; }

    public void recordYarn(UUID senderId, String body, Instant at) {
        this.lastSenderId = senderId;
        this.lastBody = body.length() > 160 ? body.substring(0, 160) : body;
        this.lastYarnAt = at;
    }
}
