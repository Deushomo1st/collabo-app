package com.collabo.backend.repository;

import com.collabo.backend.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    Optional<Space> findByPostId(UUID postId);
    boolean existsByPostId(UUID postId);
    boolean existsByOwnerId(UUID ownerId);
}
