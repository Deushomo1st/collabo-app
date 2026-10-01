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

    // Nullable wrapper (like verified): null reads as false. Marks an account
    // created while test mode was active — these skip email validation + OTP.
    @Column(name = "is_test")
    private Boolean test = false;

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    // Profile. Nullable wrappers/strings so existing rows survive the new columns:
    // null reads as "no title", "no bio" and EVERYONE.
    @Column(name = "preferred_title", length = 40)
    private String preferredTitle;

    @Column(length = 600)
    private String bio;

    @Column(name = "credentials_privacy")
    @Enumerated(EnumType.STRING)
    private CredentialsPrivacy credentialsPrivacy;

    @Column(name = "message_privacy")
    @Enumerated(EnumType.STRING)
    private MessagePrivacy messagePrivacy;

    // First-run flag. Nullable wrapper: legacy rows are null and never see the welcome flow;
    // new accounts are saved as FALSE and flipped to TRUE when they finish or skip it.
    @Column
    private Boolean welcomed;

    public boolean needsWelcome() { return Boolean.FALSE.equals(welcomed); }
    public void setWelcomed(boolean welcomed) { this.welcomed = welcomed; }

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

    // Nullable wrapper: null reads as false. Admin-set only; there is no purchase flow yet (docs leave premium unspecified).
    @Column
    private Boolean premium = false;

    public boolean isPremium() { return Boolean.TRUE.equals(premium); }
    public void setPremium(boolean premium) { this.premium = premium; }

    // null-safe: a legacy row reads as not-a-test-account.
    public boolean isTest() { return Boolean.TRUE.equals(test); }
    public void setTest(boolean test) { this.test = test; }

    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public LocalDateTime getOtpExpiresAt() { return otpExpiresAt; }
    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) { this.otpExpiresAt = otpExpiresAt; }

    public String getPreferredTitle() { return preferredTitle == null ? "" : preferredTitle; }
    public void setPreferredTitle(String preferredTitle) { this.preferredTitle = preferredTitle; }

    public String getBio() { return bio == null ? "" : bio; }
    public void setBio(String bio) { this.bio = bio; }

    public MessagePrivacy getMessagePrivacy() { return messagePrivacy == null ? MessagePrivacy.EVERYONE : messagePrivacy; }
    public void setMessagePrivacy(MessagePrivacy messagePrivacy) { this.messagePrivacy = messagePrivacy; }
    public CredentialsPrivacy getCredentialsPrivacy() { return credentialsPrivacy == null ? CredentialsPrivacy.EVERYONE : credentialsPrivacy; }
    public void setCredentialsPrivacy(CredentialsPrivacy credentialsPrivacy) { this.credentialsPrivacy = credentialsPrivacy; }
}
