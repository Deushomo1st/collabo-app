package com.collabo.backend.service;

import com.collabo.backend.dto.AdminCreateUserRequest;
import com.collabo.backend.dto.AdminUserResponse;
import com.collabo.backend.dto.UpdateRoleRequest;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.EmailAlreadyExistsException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.exception.UsernameAlreadyExistsException;
import com.collabo.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }
        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        // NOTE: no welcome email for admin-created users (test accounts shouldn't trigger Resend).
        return AdminUserResponse.from(userRepository.save(user));
    }

    public AdminUserResponse updateRole(UUID id, UpdateRoleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        user.setRole(request.role());
        return AdminUserResponse.from(userRepository.save(user));
    }

    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }
}
