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
import java.util.Map;
import java.util.UUID;

/**
 * Admin user management. Access is gated by AdminKeyFilter (X-Admin-Key header),
 * configured in SecurityConfig — these endpoints never expose the User entity
 * (it carries the BCrypt hash); everything maps through AdminUserResponse.
 * Thin HTTP shell — all logic lives in UserService.
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
    public ResponseEntity<?> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        if (userService.emailExists(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "email already registered"));
        }
        if (userService.usernameExists(request.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "username already taken"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateRoleRequest request) {
        return userService.updateRole(id, request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "user not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        if (!userService.deleteUser(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "user not found"));
        }
        return ResponseEntity.noContent().build(); // 204
    }
}
