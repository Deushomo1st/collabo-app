package com.collabo.backend.controller;

import com.collabo.backend.dto.UserDto;
import com.collabo.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth domain: registration now, login / forgot-password later.
 * Thin HTTP shell — all logic lives in AuthService.
 */
@RestController
@RequestMapping("/api/users")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody UserDto dto) {
        if (authService.emailExists(dto.getEmail())) {
            return ResponseEntity.badRequest().body("Error: This email is already registered.");
        }
        return ResponseEntity.ok(authService.register(dto));
    }
}
