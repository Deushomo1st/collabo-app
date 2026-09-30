package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** One member's stamp on a fulfilled milestone. Deleting the row is the opt-out. */
@Entity
@Table(name = "milestone_credit", uniqueConstraints = @UniqueConstraint(columnNames = {"milestone_id", "user_id"}))
public class MilestoneCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public MilestoneCredit() {}
    public MilestoneCredit(UUID milestoneId, UUID userId) { this.milestoneId = milestoneId; this.userId = userId; }

    public UUID getId() { return id; }
    public UUID getMilestoneId() { return milestoneId; }
    public UUID getUserId() { return userId; }
}
