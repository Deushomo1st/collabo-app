package com.collabo.backend.service;

import com.collabo.backend.dto.UserDto;
import com.collabo.backend.dto.UserResponse;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.EmailAlreadyExistsException;
import com.collabo.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Auth domain logic: registration, and later passwords/tokens/login.
 * Moved verbatim out of the old UserController — behavior unchanged.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public UserResponse register(UserDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException(dto.getEmail());
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // Registration always creates a USER account — the client cannot pick a
        // role here. ADMIN accounts are created only via the admin panel.
        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);
        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getUsername());
        return UserResponse.from(savedUser);
    }
}
