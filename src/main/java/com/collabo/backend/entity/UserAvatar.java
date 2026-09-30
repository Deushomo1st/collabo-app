package com.collabo.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A user's profile picture (a JPEG). Its own table so listing users never drags images along. */
@Entity
@Table(name = "user_avatar")
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 400_000)
    private byte[] image;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserAvatar() {}
    public UserAvatar(UUID userId, byte[] image) { this.userId = userId; this.image = image; }

    public UUID getUserId() { return userId; }
    public byte[] getImage() { return image; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void replace(byte[] image) { this.image = image; this.updatedAt = Instant.now(); }
}
