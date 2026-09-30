package com.collabo.backend.entity;



import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One message. The product calls it a yarn. */
@Entity
@Table(name = "yarn", indexes = @Index(name = "idx_yarn_thread_time", columnList = "thread_id, created_at"))
public class Yarn {

    public enum Kind { USER, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "sender_id")
    private UUID senderId; // null for SYSTEM yarns

    @Column(nullable = false) @Enumerated(EnumType.STRING)
    private Kind kind = Kind.USER;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    public Yarn() {}
    public Yarn(UUID threadId, UUID senderId, Kind kind, String body) {
        this.threadId = threadId; this.senderId = senderId; this.kind = kind; this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getThreadId() { return threadId; }
    public UUID getSenderId() { return senderId; }
    public Kind getKind() { return kind; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
