package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

/** One tablet on a profile: title, http(s) URL and a short note. Shown in `position` order. */
@Entity
@Table(name = "profile_link", indexes = @Index(columnList = "user_id"))
public class ProfileLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 120)
    private String note = "";

    @Column(nullable = false)
    private int position;

    public ProfileLink() {}
    public ProfileLink(UUID userId, String title, String url, String note, int position) {
        this.userId = userId; this.title = title; this.url = url; this.note = note; this.position = position;
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getNote() { return note; }
    public int getPosition() { return position; }
}
