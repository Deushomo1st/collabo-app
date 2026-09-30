package com.collabo.backend.repository;

import com.collabo.backend.entity.Collaborator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollaboratorRepository extends JpaRepository<Collaborator, UUID> {
    Optional<Collaborator> findByPostIdAndUserId(UUID postId, UUID userId);
    boolean existsByPostIdAndUserIdAndState(UUID postId, UUID userId, Collaborator.State state);
    List<Collaborator> findByPostIdAndStateInOrderByCreatedAtAsc(UUID postId, Collection<Collaborator.State> states);
    List<Collaborator> findByUserIdAndStateInOrderByCreatedAtDesc(UUID userId, Collection<Collaborator.State> states);
}
