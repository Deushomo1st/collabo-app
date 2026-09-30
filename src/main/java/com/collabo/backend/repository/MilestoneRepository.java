package com.collabo.backend.repository;

import com.collabo.backend.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findBySpaceIdOrderByCreatedAtDesc(UUID spaceId);
    Optional<Milestone> findByIdAndSpaceId(UUID id, UUID spaceId);
}
