package com.collabo.backend.repository;

import com.collabo.backend.entity.CollaboratorCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollaboratorCaseRepository extends JpaRepository<CollaboratorCase, UUID> {
    List<CollaboratorCase> findTop50ByPostIdOrderByStartedAtDesc(UUID postId);
    Optional<CollaboratorCase> findByIdAndPostId(UUID id, UUID postId);
    List<CollaboratorCase> findByPostIdAndTargetIdAndStateIn(UUID postId, UUID targetId, Collection<CollaboratorCase.State> states);
    List<CollaboratorCase> findByPostIdAndState(UUID postId, CollaboratorCase.State state);
    List<CollaboratorCase> findByStateAndDeadlineBefore(CollaboratorCase.State state, Instant now);
}
