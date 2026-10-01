package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** One voter's choice on a collaborator case: freeze them or disband them. Changeable until the case is decided. */
@Entity
@Table(name = "case_vote", uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "voter_id"}))
public class CaseVote {

    public enum Choice { FREEZE, DISBAND }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "voter_id", nullable = false)
    private UUID voterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Choice choice;

    public CaseVote() {}
    public CaseVote(UUID caseId, UUID voterId, Choice choice) { this.caseId = caseId; this.voterId = voterId; this.choice = choice; }

    public UUID getCaseId() { return caseId; }
    public UUID getVoterId() { return voterId; }
    public Choice getChoice() { return choice; }
    public void setChoice(Choice choice) { this.choice = choice; }
}
