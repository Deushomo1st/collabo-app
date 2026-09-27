package com.collabo.backend.controller;

import com.collabo.backend.dto.ResendOtpRequest;
import com.collabo.backend.dto.UserDto;
import com.collabo.backend.dto.UserResponse;
import com.collabo.backend.dto.VerifyOtpRequest;
import com.collabo.backend.service.AuthService;
import com.collabo.backend.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Auth domain: registration (email-verified via OTP) now, login later.
 * Thin HTTP shell — all logic lives in AuthService, all errors are
 * turned into clean JSON by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/users")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserDto dto,
                                                 HttpServletRequest request) {
        return ResponseEntity.ok(authService.register(dto, ClientIpResolver.resolve(request)));
    }

    @PostMapping("/verify")
    public ResponseEntity<UserResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request.email(), request.code()));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request.email());
        return ResponseEntity.ok(Map.of("message", "Verification code sent"));
    }
}
