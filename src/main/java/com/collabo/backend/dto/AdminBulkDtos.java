package com.collabo.backend.dto;

import com.collabo.backend.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Bulk account creation from the admin console. A blank password is generated and handed back once, in the result. */
public final class AdminBulkDtos {
    private AdminBulkDtos() {}

    public record Row(String email, String username, String password, Role role) {}

    public record Request(@NotEmpty @Size(max = 500) List<@Valid Row> users, Boolean test) {}

    /** One line of the outcome. `password` is set only when the server generated it. */
    public record Result(int row, String email, String username, boolean ok, String error, String password) {}
}
