package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Something a user reported from the Report page (a bug, a broken screen, abuse). The admin console lists these. Screenshots and videos are Media rows whose post_id is this report's id. */
@Entity
@Table(name = "problem_report", indexes = {@Index(columnList = "reporter_id, created_at"), @Index(columnList = "resolved, created_at")})
public class ProblemReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(nullable = false, length = 2000)
    private String summary;

    /** The page they were on when they pressed Report (a path on this site), if it was one. */
    @Column(name = "page_url", length = 300)
    private String pageUrl;

    @Column(nullable = false)
    private boolean resolved;

    /** The admin console shows "Anonymous" instead of the reporter's name. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean anonymous;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ProblemReport() {}
    public ProblemReport(UUID reporterId, String summary, String pageUrl) { this.reporterId = reporterId; this.summary = summary; this.pageUrl = pageUrl; }

    public UUID getId() { return id; }
    public UUID getReporterId() { return reporterId; }
    public String getSummary() { return summary; }
    public String getPageUrl() { return pageUrl; }
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    public boolean isResolved() { return resolved; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setResolved(boolean resolved) { this.resolved = resolved; this.resolvedAt = resolved ? Instant.now() : null; }
}
