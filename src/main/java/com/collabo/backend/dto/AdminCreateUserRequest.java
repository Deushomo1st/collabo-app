package com.collabo.backend.dto;

import com.collabo.backend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotNull Role role
) {
}
