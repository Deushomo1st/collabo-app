package com.collabo.backend.dto;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public view of a user (registration response). NEVER expose the User entity
 * itself — it carries the BCrypt hash.
 */
public record UserResponse(
        UUID id,
        String email,
        String username,
        Role role,
        boolean verified,
        boolean test,
        boolean needsWelcome,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.isVerified(),
                user.isTest(),
                user.needsWelcome(),
                user.getCreatedAt()
        );
    }
}
