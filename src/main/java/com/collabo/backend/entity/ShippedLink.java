package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

/** Proof of shipping: a link the owner attaches to one of their credential entries. */
@Entity
@Table(name = "shipped_link", indexes = @Index(columnList = "entry_id"))
public class ShippedLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    public ShippedLink() {}
    public ShippedLink(UUID entryId, String title, String url) { this.entryId = entryId; this.title = title; this.url = url; }

    public UUID getId() { return id; }
    public UUID getEntryId() { return entryId; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
}
