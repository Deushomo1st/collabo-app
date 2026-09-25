package com.collabo.backend.controller; // <-- Match your actual package name

import com.collabo.backend.dto.UserDto;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    // 1. Declare the dependencies as final
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // 2. Single constructor that injects all of them
    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // 3. The endpoint
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserDto dto) {

        // Convert the DTO into a real User entity
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());

        // Hash the password before saving!
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        // Convert the text "USER" or "ADMIN" into our Role Enum
        user.setRole(Role.valueOf(dto.getRole().toUpperCase()));

        // Save it to the database
        User savedUser = userRepository.save(user);

        // Send the welcome email
        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getUsername());

        // Send the response back to the client
        return ResponseEntity.ok(savedUser);
    }
}