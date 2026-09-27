package com.collabo.backend.entity; // <-- Keep your actual package name here!

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID; // <-- NEW IMPORT
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // <-- CHANGED TO UUID
    private UUID id; // <-- CHANGED TYPE TO UUID

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING) // This saves "USER" or "ADMIN" as text in the DB, not a number
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Wrapper (nullable) so Hibernate can add the column to a table that already
    // has rows without a NOT NULL failure; null is treated as unverified.
    @Column
    private Boolean verified = false;

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---
    public UUID getId() { return id; } // <-- UPDATED RETURN TYPE
    public void setId(UUID id) { this.id = id; } // <-- UPDATED PARAM TYPE

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // null-safe: a legacy row (created before verification existed) reads as unverified.
    public boolean isVerified() { return Boolean.TRUE.equals(verified); }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public LocalDateTime getOtpExpiresAt() { return otpExpiresAt; }
    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) { this.otpExpiresAt = otpExpiresAt; }
}
