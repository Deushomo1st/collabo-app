package com.collabo.backend.controller;

import com.collabo.backend.dto.AdminCreateUserRequest;
import com.collabo.backend.dto.AdminUserResponse;
import com.collabo.backend.dto.UpdateRoleRequest;
import com.collabo.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin user management. Access is gated by AdminKeyFilter (X-Admin-Key header),
 * configured in SecurityConfig — these endpoints never expose the User entity
 * (it carries the BCrypt hash); everything maps through AdminUserResponse.
 * Thin HTTP shell — logic lives in UserService, errors become clean JSON
 * via GlobalExceptionHandler (409 conflict / 404 not found).
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return userService.listUsers();
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PostMapping("/bulk")
    public List<com.collabo.backend.dto.AdminBulkDtos.Result> createUsers(@Valid @RequestBody com.collabo.backend.dto.AdminBulkDtos.Request request) {
        return userService.createUsers(request);
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateRoleRequest request) {
        return userService.updateRole(id, request);
    }

    @PatchMapping("/{id}/premium")
    public AdminUserResponse setPremium(@PathVariable UUID id, @RequestBody com.collabo.backend.dto.RemovalRecordDtos.PremiumRequest request) {
        return userService.setPremium(id, request.premium());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build(); // 204
    }
}
