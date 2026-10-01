package com.collabo.backend.repository;

import com.collabo.backend.entity.ApplicationReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationReactionRepository extends JpaRepository<ApplicationReaction, UUID> {
    List<ApplicationReaction> findByApplicationId(UUID applicationId);
    List<ApplicationReaction> findByApplicationIdIn(Collection<UUID> applicationIds);
    Optional<ApplicationReaction> findByApplicationIdAndUserId(UUID applicationId, UUID userId);
}
