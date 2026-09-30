package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A party to a removal (the removed person or the one who flagged them) speaking for the record. */
@Entity
@Table(name = "removal_address", indexes = @Index(name = "idx_address_record", columnList = "record_id, created_at"))
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Address() {}
    public Address(UUID recordId, UUID authorId, String body) { this.recordId = recordId; this.authorId = authorId; this.body = body; }

    public UUID getId() { return id; }
    public UUID getRecordId() { return recordId; }
    public UUID getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
