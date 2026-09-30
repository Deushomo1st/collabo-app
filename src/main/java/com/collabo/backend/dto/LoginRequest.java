package com.collabo.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** identifier = the email or the username. */
public record LoginRequest(
        @NotBlank(message = "Enter your email or username") String identifier,
        @NotBlank(message = "Enter your password") String password) {}
