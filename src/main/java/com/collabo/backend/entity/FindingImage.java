package com.collabo.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A screenshot attached to a finding. Its own table so listing findings never drags images along. */
@Entity
@Table(name = "finding_image", indexes = @Index(name = "idx_finding_image_finding", columnList = "finding_id"))
public class FindingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "finding_id", nullable = false)
    private UUID findingId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false, length = 1_500_000)
    private byte[] image;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public FindingImage() {}
    public FindingImage(UUID findingId, String contentType, byte[] image) { this.findingId = findingId; this.contentType = contentType; this.image = image; }

    public UUID getId() { return id; }
    public UUID getFindingId() { return findingId; }
    public String getContentType() { return contentType; }
    public byte[] getImage() { return image; }
}
