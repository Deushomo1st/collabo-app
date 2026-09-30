package com.collabo.backend.repository;

import com.collabo.backend.entity.Moderator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModeratorRepository extends JpaRepository<Moderator, UUID> {
    Optional<Moderator> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Moderator> findAllByOrderByCreatedAtAsc();
}
