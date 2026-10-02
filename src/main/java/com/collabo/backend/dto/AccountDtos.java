package com.collabo.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Account security: forgot/reset password, change password, signed-in devices, delete account. */
public final class AccountDtos {

    private AccountDtos() {}

    public record ForgotRequest(@NotBlank String email) {}
    public record ResetRequest(@NotBlank String email, @NotBlank String code, @NotBlank String password) {}
    public record ChangePasswordRequest(@NotBlank String current, @NotBlank String password) {}
    public record CodeRequest(@NotBlank String code) {}
    public record ConfirmRequest(@NotBlank String password) {}

    /** id is an opaque handle, never the real session id, so a leaked page can't hijack another device. */
    public record SessionInfo(String id, boolean current, String device, Instant createdAt, Instant lastActive) {}
}
