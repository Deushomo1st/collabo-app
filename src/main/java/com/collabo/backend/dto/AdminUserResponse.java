package com.collabo.backend.dto;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin-facing view of a user. NEVER expose the User entity itself — it carries the BCrypt hash.
 */
public record AdminUserResponse(
        UUID id,
        String email,
        String username,
        Role role,
        boolean test,
        boolean verified,
        boolean premium,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.isTest(),
                user.isVerified(),
                user.isPremium(),
                user.getCreatedAt()
        );
    }
}
