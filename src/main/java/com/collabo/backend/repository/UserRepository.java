package com.collabo.backend.repository;

import com.collabo.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // This automatically generates the SQL: SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // This checks if an email already exists "na just registration yarns"
    boolean existsByEmail(String email);

    // Same check for username (used by the admin create-user endpoint)
    boolean existsByUsername(String username);
}