package com.collabo.backend.service;

import com.collabo.backend.dto.AdminCreateUserRequest;
import com.collabo.backend.dto.AdminUserResponse;
import com.collabo.backend.dto.UpdateRoleRequest;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User domain logic: database queries and validation for user management.
 * Controllers delegate here instead of touching the repository directly.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        // NOTE: no welcome email for admin-created users (test accounts shouldn't trigger Resend).
        return AdminUserResponse.from(userRepository.save(user));
    }

    public Optional<AdminUserResponse> updateRole(UUID id, UpdateRoleRequest request) {
        return userRepository.findById(id).map(user -> {
            user.setRole(request.role());
            return AdminUserResponse.from(userRepository.save(user));
        });
    }

    public boolean deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        userRepository.deleteById(id);
        return true;
    }
}
