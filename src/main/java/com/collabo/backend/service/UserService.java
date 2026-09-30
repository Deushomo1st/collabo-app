package com.collabo.backend.service;

import com.collabo.backend.dto.AdminBulkDtos;
import com.collabo.backend.dto.AdminCreateUserRequest;
import com.collabo.backend.dto.AdminUserResponse;
import com.collabo.backend.dto.UpdateRoleRequest;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.EmailAlreadyExistsException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.exception.UsernameAlreadyExistsException;
import com.collabo.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
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
        // Same normalization as public registration (email case-insensitive, username trimmed).
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setTest(request.test());
        // NOTE: no welcome email for admin-created users (test accounts shouldn't trigger Resend).
        return AdminUserResponse.from(userRepository.save(user));
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 12 random characters plus one symbol, so it passes the same strength rule as registration. */
    private static String generatePassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return sb.append("!@#$%&*?".charAt(RANDOM.nextInt(8))).append(RANDOM.nextInt(10)).toString();
    }

    /** Creates each row on its own: one bad row is reported and the rest still go through. */
    public List<AdminBulkDtos.Result> createUsers(AdminBulkDtos.Request request) {
        List<AdminBulkDtos.Result> results = new ArrayList<>();
        int n = 0;
        for (AdminBulkDtos.Row r : request.users()) {
            n++;
            String email = r.email() == null ? "" : r.email().trim();
            String username = r.username() == null ? "" : r.username().trim();
            boolean generated = r.password() == null || r.password().isBlank();
            String password = generated ? generatePassword() : r.password();
            try {
                if (!email.contains("@")) throw new IllegalArgumentException("Not a valid email.");
                if (username.isEmpty()) throw new IllegalArgumentException("Username is missing.");
                if (password.length() < 8) throw new IllegalArgumentException("Password is shorter than 8 characters.");
                createUser(new AdminCreateUserRequest(email, username, password, r.role() == null ? Role.USER : r.role(), Boolean.TRUE.equals(request.test())));
                results.add(new AdminBulkDtos.Result(n, email, username, true, null, generated ? password : null));
            } catch (RuntimeException e) {
                String why = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
                results.add(new AdminBulkDtos.Result(n, email, username, false, why, null));
            }
        }
        return results;
    }

    public AdminUserResponse updateRole(UUID id, UpdateRoleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        user.setRole(request.role());
        return AdminUserResponse.from(userRepository.save(user));
    }

    public AdminUserResponse setPremium(UUID id, boolean premium) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        user.setPremium(premium);
        return AdminUserResponse.from(userRepository.save(user));
    }

    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }
}
