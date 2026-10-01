package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** One reviewer's agree or disagree on an application. Changeable until the space forms. */
@Entity
@Table(name = "application_reaction", uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "user_id"}))
public class ApplicationReaction {

    public enum Kind { AGREE, DISAGREE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    public ApplicationReaction() {}
    public ApplicationReaction(UUID applicationId, UUID userId, Kind kind) { this.applicationId = applicationId; this.userId = userId; this.kind = kind; }

    public UUID getApplicationId() { return applicationId; }
    public UUID getUserId() { return userId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
}
