package com.collabo.backend.dto;

import com.collabo.backend.entity.Moderator;

import java.time.Instant;
import java.util.UUID;

public final class ModeratorDtos {
    private ModeratorDtos() {}

    public record CreateRequest(String name, String email, String password) {}

    public record ActiveRequest(boolean active) {}

    /** The admin setting a new password for a moderator. */
    public record PasswordRequest(String password) {}

    /** A moderator changing their own password. */
    public record ChangePasswordRequest(String current, String password) {}

    /** Never carries the password hash. */
    public record ModeratorView(UUID id, String name, String email, boolean active, Instant createdAt) {
        public static ModeratorView of(Moderator m) { return new ModeratorView(m.getId(), m.getName(), m.getEmail(), m.isActive(), m.getCreatedAt()); }
    }
}
