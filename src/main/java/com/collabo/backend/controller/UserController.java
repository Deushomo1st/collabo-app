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

        // 1. Check if the email is already taken
        if (userRepository.existsByEmail(dto.getEmail())) {
            return ResponseEntity.badRequest().body("Error: This email is already registered.");
        }

        // 2. Convert the DTO into a real User entity
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // Public registration is ALWAYS a plain USER — the dto role is ignored so
        // nobody can self-register as ADMIN. Role elevation happens via /api/admin only.
        user.setRole(Role.USER);

        // 3. Save it to the database
        User savedUser = userRepository.save(user);

        // 4. Send the welcome email
        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getUsername());

        // 5. Send the response back
        return ResponseEntity.ok(savedUser);
    }
}