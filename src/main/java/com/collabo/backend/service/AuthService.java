package com.collabo.backend.service;

import com.collabo.backend.dto.UserDto;
import com.collabo.backend.dto.UserResponse;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.EmailAlreadyExistsException;
import com.collabo.backend.exception.PasswordValidationException;
import com.collabo.backend.exception.RateLimitChallengeException;
import com.collabo.backend.exception.RateLimitedException;
import com.collabo.backend.exception.UsernameAlreadyExistsException;
import com.collabo.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Auth domain logic: registration, and later passwords/tokens/login.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RegistrationRateLimiter rateLimiter;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       RegistrationRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
    }

    public UserResponse register(UserDto dto, String clientIp) {
        RegistrationRateLimiter.RateLimitResult rl =
                rateLimiter.check(clientIp, dto.getChallengeToken(), dto.getChallengeAnswer());

        if (rl.challenge() != null) {
            throw new RateLimitChallengeException(rl.challenge().question(), rl.challenge().token());
        }
        if (rl.retryAfterSeconds() > 0) {
            throw new RateLimitedException(rl.retryAfterSeconds());
        }

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new UsernameAlreadyExistsException();
        }

        String password = dto.getPassword();
        // Hard floor: even the "proceed anyway" path can't go below 6 chars.
        if (password.length() < 6) {
            throw new PasswordValidationException("Password must be at least 6 characters");
        }
        // Weak passwords are only accepted if the client explicitly acknowledged
        // them via the proceed-anyway flow.
        if (!isSecurePassword(password) && !dto.isWeakPasswordAccepted()) {
            throw new PasswordValidationException("Password not yet secure");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(password));
        // Registration always creates a USER account — the client cannot pick a
        // role here. ADMIN accounts are created only via the admin panel.
        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);
        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getUsername());
        return UserResponse.from(savedUser);
    }

    /** "Secure" = 8+ chars with uppercase + lowercase + digit + special. */
    private boolean isSecurePassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean upper = false, lower = false, digit = false, special = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else special = true;
        }
        return upper && lower && digit && special;
    }
}
