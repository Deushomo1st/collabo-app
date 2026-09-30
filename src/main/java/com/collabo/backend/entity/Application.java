package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Someone's pitch to join the idea in a post: a short "why you" statement and where the founder has taken it. */
@Entity
@Table(name = "application", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "applicant_id"}),
        indexes = {@Index(columnList = "applicant_id, created_at"), @Index(columnList = "post_id, created_at")})
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(nullable = false, length = 1500)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ApplicationState state = ApplicationState.SUBMITTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Application() {}
    public Application(UUID postId, UUID applicantId, String statement) {
        this.postId = postId; this.applicantId = applicantId; this.statement = statement;
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getApplicantId() { return applicantId; }
    public String getStatement() { return statement; }
    public ApplicationState getState() { return state; }
    public void setState(ApplicationState state) { this.state = state; }
    public Instant getCreatedAt() { return createdAt; }
}
