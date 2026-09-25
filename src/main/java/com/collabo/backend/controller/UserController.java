package com.collabo.backend.controller; // <-- Match your package name

import com.collabo.backend.dto.UserDto;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    // Update the constructor to inject it
    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // This creates the endpoint: POST https://collaboapp.pro/api/users
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserDto dto) {

        // 1. Convert the DTO (envelope) into a real User entity
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        // Hash the password before saving!
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        // Convert the text "USER" or "ADMIN" into our Role Enum
        user.setRole(Role.valueOf(dto.getRole().toUpperCase()));

        // 2. Save it to the database
        User savedUser = userRepository.save(user);

        // 3. Send it back to the user
        return ResponseEntity.ok(savedUser);
    }
}