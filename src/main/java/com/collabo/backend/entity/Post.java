package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An idea posted to The Gaze. applyBy null means the application window is open indefinitely. */
@Entity
@Table(name = "post", indexes = {@Index(columnList = "author_id"), @Index(columnList = "created_at")})
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "apply_by")
    private Instant applyBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Set once a space has been formed from this post. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean formed;

    /** Space-separated, lower case, no '#'. Null when there are none. */
    @Column(length = 400)
    private String hashtags;

    @Column(name = "comments_on", nullable = false, columnDefinition = "boolean default true")
    private boolean commentsOn = true;

    @Column(name = "shouts_on", nullable = false, columnDefinition = "boolean default true")
    private boolean shoutsOn = true;

    /** false = a regular post: nobody can apply to it. */
    @Column(name = "applications_on", nullable = false, columnDefinition = "boolean default true")
    private boolean applicationsOn = true;

    /** Who may see it: EVERYONE, FOLLOWERS (the author's followers), ONLY (the people in audienceList) or EXCEPT (everyone but them). The author always sees it. */
    @Column(nullable = false, length = 12, columnDefinition = "varchar(12) default 'EVERYONE'")
    private String audience = "EVERYONE";

    /** Space-separated user ids for ONLY / EXCEPT. */
    @Column(name = "audience_list", length = 4000)
    private String audienceList;

    /** Others see "Anonymous" instead of the author. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean anonymous;

    /** How many times someone other than the author has opened the post. */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long views;

    public Post() {}
    public long getViews() { return views; }
    public Post(UUID authorId, String title, String body, Instant applyBy) {
        this.authorId = authorId; this.title = title; this.body = body; this.applyBy = applyBy;
    }

    public UUID getId() { return id; }
    public UUID getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getApplyBy() { return applyBy; }
    public void setApplyBy(Instant applyBy) { this.applyBy = applyBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<String> tags() { return hashtags == null || hashtags.isBlank() ? List.of() : List.of(hashtags.split(" ")); }
    public void setTags(List<String> tags) { this.hashtags = tags.isEmpty() ? null : String.join(" ", tags); }
    public boolean isCommentsOn() { return commentsOn; }
    public void setCommentsOn(boolean commentsOn) { this.commentsOn = commentsOn; }
    public boolean isApplicationsOn() { return applicationsOn; }
    public void setApplicationsOn(boolean applicationsOn) { this.applicationsOn = applicationsOn; }
    public boolean isShoutsOn() { return shoutsOn; }
    public void setShoutsOn(boolean shoutsOn) { this.shoutsOn = shoutsOn; }
    public String getAudience() { return audience == null ? "EVERYONE" : audience; }
    public java.util.Set<String> audienceIds() { return audienceList == null || audienceList.isBlank() ? java.util.Set.of() : java.util.Set.of(audienceList.split(" ")); }
    public void setAudience(String audience, java.util.Collection<UUID> ids) {
        this.audience = audience; this.audienceList = ids.isEmpty() ? null : String.join(" ", ids.stream().map(UUID::toString).toList());
    }
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    public boolean isFormed() { return formed; }
    public void setFormed(boolean formed) { this.formed = formed; }

    /** formed once a space exists; otherwise pending while the window is open and closed once it ends. */
    public String status() { return formed ? "formed" : applyBy == null || applyBy.isAfter(Instant.now()) ? "pending" : "closed"; }
}
