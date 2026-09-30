package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment the team documents. COLLABO never moves money: the payer states it was paid (CLAIMED), the recipient states it
 * arrived (CONFIRMED), or the payer withdraws the claim (CANCELLED). While CLAIMED, the space's room is held.
 */
@Entity
@Table(name = "payment_record", indexes = @Index(name = "idx_payment_space", columnList = "space_id, created_at"))
public class PaymentRecord {

    public enum State { CLAIMED, CONFIRMED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 5)
    private String currency;

    @Column(nullable = false, length = 200)
    private String note = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private State state = State.CLAIMED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public PaymentRecord() {}
    public PaymentRecord(UUID spaceId, UUID payerId, UUID recipientId, BigDecimal amount, String currency, String note) {
        this.spaceId = spaceId; this.payerId = payerId; this.recipientId = recipientId;
        this.amount = amount; this.currency = currency; this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public UUID getPayerId() { return payerId; }
    public UUID getRecipientId() { return recipientId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getNote() { return note; }
    public State getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void resolve(State next) { this.state = next; this.resolvedAt = Instant.now(); }
}
