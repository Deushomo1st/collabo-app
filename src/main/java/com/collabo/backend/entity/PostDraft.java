package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A post someone saved to finish later. Everything is optional; only the owner ever sees it. */
@Entity
@Table(name = "post_draft", indexes = @Index(columnList = "author_id"))
public class PostDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(length = 120) private String title;
    @Column(length = 2000) private String body;
    @Column(name = "apply_by") private Instant applyBy;
    @Column(length = 400) private String hashtags;                 // space separated
    @Column(name = "media_ids", length = 400) private String mediaIds;   // comma separated media ids, in order
    @Column(name = "share_with", length = 1000) private String shareWith; // comma separated usernames
    @Column(name = "comments_on", nullable = false) private boolean commentsOn = true;
    @Column(name = "shouts_on", nullable = false) private boolean shoutsOn = true;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    public PostDraft() {}
    public PostDraft(UUID authorId) { this.authorId = authorId; }

    public UUID getId() { return id; }
    public UUID getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getApplyBy() { return applyBy; }
    public String getHashtags() { return hashtags; }
    public String getMediaIds() { return mediaIds; }
    public String getShareWith() { return shareWith; }
    public boolean isCommentsOn() { return commentsOn; }
    public boolean isShoutsOn() { return shoutsOn; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void fill(String title, String body, Instant applyBy, String hashtags, String mediaIds, String shareWith, boolean commentsOn, boolean shoutsOn) {
        this.title = title; this.body = body; this.applyBy = applyBy; this.hashtags = hashtags; this.mediaIds = mediaIds;
        this.shareWith = shareWith; this.commentsOn = commentsOn; this.shoutsOn = shoutsOn; this.updatedAt = Instant.now();
    }
}
