package com.collabo.backend.dto;

import com.collabo.backend.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull Role role
) {
}
