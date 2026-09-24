package com.collabo.backend.entity; // <-- Keep your actual package name here!

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID; // <-- NEW IMPORT

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
